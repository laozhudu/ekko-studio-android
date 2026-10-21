package com.laozhudu.ekkostudio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The coverage baseline is a ratchet: it may go up, never down.
 *
 * `docs/api-coverage-baseline.json` is produced by `scripts/api_coverage.py`
 * against the vendored OpenAPI snapshot, and this test re-checks it with the
 * same matching rule the script uses, so the two cannot drift:
 *
 *  - both paths are split into segments
 *  - a segment matches only when equal, or when both sides are interpolated
 *    (`{}`), or when one side is a literal plus interpolation (`assign${x}` vs
 *    `assign`)
 *  - segment counts must agree
 *
 * The strict rule matters. An earlier version matched by string prefix, which
 * credited every sibling route of a module and reported 85% coverage when the
 * real figure was 49%. A wildcard must never absorb a literal route.
 *
 * To raise the baseline: implement the calls, run
 *   python3 scripts/api_coverage.py --json
 * and regenerate the file. Its diff is the API progress report.
 */
class ApiCoverageTest {

    private val baseline = JSONObject(File("../../docs/api-coverage-baseline.json").readText())

    private val sources: List<File> = File("src/main/java/com/laozhudu/ekkostudio")
        .listFiles()
        .orEmpty()
        .filter { it.extension == "kt" }

    private fun segments(path: String): List<String> {
        var value = path.substringBefore('?').trimEnd('/')
        // A trailing interpolation may contain nested braces or quotes; collapse
        // from the last `$` when no separator follows it.
        val dollar = value.indexOf('$')
        if (dollar >= 0 && !value.substring(dollar).contains('/')) {
            value = value.substring(0, dollar) + "{}"
        }
        value = value
            .replace(Regex("\\$\\{[^}]*}"), "{}")
            .replace(Regex("\\$[A-Za-z_][A-Za-z0-9_]*"), "{}")
            .replace(Regex("\\{[^}]*}"), "{}")
        return value.split("/").filter { it.isNotEmpty() }
    }

    private fun segmentMatches(client: String, spec: String): Boolean {
        if (client == spec) return true
        if (client.endsWith("{}") && spec == client.dropLast(2)) return true
        if (spec.endsWith("{}") && client == spec.dropLast(2)) return true
        return false
    }

    private fun matches(clientPath: String, specPath: String): Boolean {
        val c = segments(clientPath)
        val s = segments(specPath)
        return c.size == s.size && c.indices.all { segmentMatches(c[it], s[it]) }
    }

    private val clientPaths: List<String> by lazy {
        sources.flatMap { file ->
            Regex("\"(/api/[^\"]*)\"").findAll(file.readText()).map { it.groupValues[1] }
        }
    }

    private fun covered(path: String): Boolean = clientPaths.any { matches(it, path) }

    @Test
    fun everyOperationMarkedUsedIsStillCalled() {
        val used = baseline.getJSONArray("usedPaths")
        val missing = (0 until used.length())
            .map { used.getString(it).substringAfter(' ') }
            .filter { !covered(it) }

        assertTrue(
            "these operations are in the coverage baseline but the client no longer calls them:\n" +
                missing.joinToString("\n") +
                "\nRestore the call or regenerate docs/api-coverage-baseline.json.",
            missing.isEmpty(),
        )
    }

    @Test
    fun theBaselineCountsMatchTheServerSnapshot() {
        val total = baseline.getInt("total")
        val used = baseline.getInt("used")
        val oos = baseline.getInt("outOfScope")
        val gap = baseline.getInt("gap")

        assertEquals("total must equal used + out-of-scope + gap", total, used + oos + gap)
        assertEquals("every used operation is listed", used, baseline.getJSONArray("usedPaths").length())
        assertTrue("the snapshot covers the whole API", total >= 500)
    }

    /**
     * Pins the matcher itself: a future "simplification" back to prefix matching
     * would silently inflate the number again.
     */
    @Test
    fun aWildcardNeverAbsorbsALiteralRoute() {
        assertTrue(matches("/api/hermes/kanban/complete", "/api/hermes/kanban/complete"))
        assertTrue(
            "a literal route is not the templated one",
            !matches("/api/hermes/kanban/complete", "/api/hermes/kanban/{id}"),
        )
        assertTrue(matches("/api/studio/sessions/\${enc(id)}/pin", "/api/studio/sessions/{id}/pin"))
        assertTrue(matches("/api/hermes/mcp/reload\${query}", "/api/hermes/mcp/reload"))
        assertTrue(!matches("/api/studio/sessions/{id}/pin", "/api/studio/sessions/{id}/usage"))
    }
}
