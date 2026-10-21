package com.laozhudu.ekkostudio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the client against drifting away from the server's published API.
 *
 * `src/test/resources/openapi-paths.txt` is the path list exported from a live
 * `/api/openapi.json` (Ekko Studio 0.7.38, 529 operations). Every `/api/...`
 * literal in the network layer must appear there, or be listed in [allowed] with
 * a reason. This is the check that would have caught the six `/api/devices/…`
 * routes that had been 404 for months, and the `/api/chat-run/runs` fallback
 * that never worked.
 *
 * Re-export after upgrading the server:
 *   curl -s -H "Authorization: Bearer $TOKEN" http://HOST/api/openapi.json \
 *     | python3 -c 'import json,sys; d=json.load(sys.stdin); [print(m.upper(), p) for p,i in d["paths"].items() for m in i if m in ("get","post","put","patch","delete")]' \
 *     | sort > android/app/src/test/resources/openapi-paths.txt
 */
class ApiPathAlignmentTest {

    private val published: Set<String> =
        File("src/test/resources/openapi-paths.txt").readLines()
            .filter { it.isNotBlank() }
            .map { normalize(it.substringAfter(' ')) }
            .toSet()

    /**
     * Deliberate deviations. Each entry needs a reason; this is the only place a
     * path may live outside the published list.
     */
    private val allowed = mapOf(
        // Legacy fallbacks: documented under /api/studio, the /api/hermes alias is
        // tried second so an older server still answers.
        "/api/hermes/sessions" to "fallback for /api/studio/sessions",
        "/api/hermes/sessions/{}" to "fallback for /api/studio/sessions/{id}",
        "/api/hermes/sessions/{}/model" to "fallback for /api/studio/sessions/{id}/model",
        "/api/hermes/sessions/{}/rename" to "fallback for /api/studio/sessions/{id}/rename",
        "/api/hermes/sessions/context-length" to "fallback for the studio route",
        // Compatibility alias kept for the sessions list fallback.
        "/api/hermes/download" to "superseded by /api/studio/files/download",
        // WebSocket upgrade endpoints never appear in the OpenAPI document.
        "/api/hermes/terminal" to "websocket upgrade, not part of the REST spec",
    )

    /** `${x}` / `$x` / `{id}` all collapse to `{}`, query strings are dropped. */
    private fun normalize(path: String): String = path
        .substringBefore('?')
        .replace(Regex("\\$\\{[^}]*}"), "{}")
        .replace(Regex("\\$[A-Za-z_][A-Za-z0-9_]*"), "{}")
        .replace(Regex("\\{[^}]*}"), "{}")
        .trimEnd('/')

    private fun isPublished(call: String): Boolean {
        // Interpolated paths cannot be compared exactly — a literal like
        // `/api/hermes/kanban$suffix` or `/api/hermes/jobs/${id}/$action` only
        // resolves at runtime, and one of them even embeds a quote that truncates
        // the captured text. For those, require the module family to be published;
        // every static path is still matched character for character, which is
        // where the real bugs were (`/api/devices`, `/api/chat-run/runs`).
        if (call.contains('$')) {
            // Module-level check for interpolated paths: the literal only
            // resolves at runtime, so require /api/<ns>/<module> to be published.
            val family = normalize(call.substringBefore('$')).split("/").take(4).joinToString("/")
            return family.isNotEmpty() && published.any { it == family || it.startsWith("$family/") }
        }
        return normalize(call) in published
    }

    @Test
    fun everyCallIsEitherPublishedOrExplicitlyAllowed() {
        val sources = listOf(
            "src/main/java/com/laozhudu/ekkostudio/HermesApi.kt",
            "src/main/java/com/laozhudu/ekkostudio/ChatSocket.kt",
            "src/main/java/com/laozhudu/ekkostudio/GroupSocket.kt",
            "src/main/java/com/laozhudu/ekkostudio/TerminalClient.kt",
        ).map { File(it) }

        val offenders = mutableListOf<String>()
        sources.forEach { file ->
            Regex("\"(/api/[^\"]*)\"").findAll(file.readText()).forEach { match ->
                val call = match.groupValues[1]
                if (normalize(call) in allowed) return@forEach
                if (!isPublished(call)) offenders += "${file.name}: $call"
            }
        }

        assertTrue(
            "not in the published OpenAPI list and not whitelisted:\n" +
                offenders.distinct().joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * Kotlin block comments nest, so a slash-star sequence inside a doc comment
     * opens a second comment that never closes and swallows the rest of the file.
     * That mistake cost three red CI runs, so it is now a test.
     */
    @Test
    fun noCommentLineOpensANestedBlockComment() {
        val roots = listOf("src/main/java/com/laozhudu/ekkostudio", "src/test/java/com/laozhudu/ekkostudio")
        val offenders = roots.flatMap { root ->
            (File(root).listFiles() ?: emptyArray())
                .filter { it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        val trimmed = line.trim()
                        val isCommentBody = trimmed.startsWith("*") || trimmed.startsWith("//")
                        if (isCommentBody && trimmed.contains("/*")) "${file.name}:${index + 1}" else null
                    }
                }
        }
        assertTrue(
            "a '/*' inside a comment opens a nested block comment:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun thePreviouslyBrokenRoutesAreGone() {
        val api = File("src/main/java/com/laozhudu/ekkostudio/HermesApi.kt").readText()
        listOf(
            "/api/devices\"",
            "/api/devices/pairing-link",
            "/api/devices/manual-request",
            "/api/devices/peer-connections",
            "\"/api/chat-run/runs\"",
        ).forEach { dead ->
            assertTrue("dead route came back: $dead", !api.contains(dead))
        }
    }

    @Test
    fun migratedCallsUseTheDocumentedStudioNamespace() {
        val api = File("src/main/java/com/laozhudu/ekkostudio/HermesApi.kt").readText()
        listOf(
            "/api/studio/sessions/context-length",
            "/api/studio/usage/stats",
            "/api/studio/performance/runtime",
            "/api/studio/petdex/manifest",
            "/api/studio/pets/active",
            "/api/studio/stt/transcribe",
            "/api/studio/tts/synthesize",
            "/api/studio/group-chat/rooms",
            "/api/studio/files/download",
        ).forEach { path ->
            assertTrue("expected the documented route $path", api.contains(path))
        }
    }
}
