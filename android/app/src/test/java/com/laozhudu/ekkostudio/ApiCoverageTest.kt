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
 * against the vendored OpenAPI snapshot. This test re-checks that every
 * operation the client claimed to use is still reachable from the network layer,
 * so a refactor cannot quietly drop API coverage, and a new module cannot be
 * declared "implemented" while its call has gone missing.
 *
 * To raise the baseline: implement the calls, run
 *   python3 scripts/api_coverage.py --json
 * and regenerate the file. The diff of that file is the API progress report.
 */
class ApiCoverageTest {

    private val baseline = JSONObject(File("../../docs/api-coverage-baseline.json").readText())

    private val sources: List<File> = File("src/main/java/com/laozhudu/ekkostudio")
        .listFiles()
        .orEmpty()
        .filter { it.extension == "kt" }

    private fun normalize(path: String): String = path
        .substringBefore('?')
        .replace(Regex("\\$\\{[^}]*}"), "{}")
        .replace(Regex("\\$[A-Za-z_][A-Za-z0-9_]*"), "{}")
        .replace(Regex("\\{[^}]*}"), "{}")
        .trimEnd('/')

    private val clientPaths: Set<String> by lazy {
        sources.flatMap { file ->
            Regex("\"(/api/[^\"]*)\"").findAll(file.readText()).map { normalize(it.groupValues[1]) }
        }.toSet()
    }

    private fun covered(path: String): Boolean {
        val norm = normalize(path)
        if (norm in clientPaths) return true
        // Interpolated paths on either side only line up by prefix: the spec has
        // /api/hermes/kanban/boards while the client builds /api/hermes/kanban$suffix.
        return clientPaths.any { client ->
            client.length > 16 && (norm.startsWith("$client/") || client.startsWith("$norm/"))
        }
    }

    @Test
    fun everyOperationMarkedUsedIsStillCalled() {
        val used = baseline.getJSONArray("usedPaths")
        val missing = (0 until used.length())
            .map { used.getString(it) }
            .filter { entry -> !covered(entry.substringAfter(' ')) }

        assertTrue(
            "these operations are in the coverage baseline but the client no longer calls them:\n" +
                missing.joinToString("\n") +
                "\nEither restore the call or regenerate docs/api-coverage-baseline.json.",
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
}
