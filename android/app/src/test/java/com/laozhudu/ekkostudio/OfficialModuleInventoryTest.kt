package com.laozhudu.ekkostudio

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ratchet for "reproduce the official client, module by module".
 *
 * `module-inventory.json` is generated from the official 1.0.8 APK by
 * `scripts/official_module_inventory.py`: 50 feature modules, each with the
 * number of stylesheet classes the official client devotes to it and whether
 * our own sources mention the concept at all. It contains counts and file names
 * only — no vendor markup, style or copy.
 *
 * Three rules, each of which has already caught a real omission:
 *
 *  1. the module list cannot silently shrink — an upgrade of the APK that drops
 *     a module has to be recorded here on purpose;
 *  2. every module must be accounted for in `docs/official-parity-plan.md`,
 *     either as a row in the matrix or in the "not doing" table;
 *  3. the number of modules we have nothing for may only go down.
 */
class OfficialModuleInventoryTest {
    private val inventory = File("src/test/resources/module-inventory.json")

    /** Official modules with no counterpart in our sources at 0.17.x. Shrink only. */
    private val knownMissingOfficial =
        setOf("network-mode", "drawer", "presets", "inbox", "location")

    /** Screens we have that the official client does not. Pinned so we do not
     *  pretend to "align" a module the official client never had. */
    private val knownOursOnly = setOf("journey", "kanban", "webhooks", "insights")

    private fun modules(): JSONArray {
        assertTrue("module inventory is missing; run scripts/official_module_inventory.py", inventory.exists())
        return org.json.JSONObject(inventory.readText()).getJSONArray("modules")
    }

    private fun moduleNames(): List<String> =
        (0 until modules().length()).map { modules().getJSONObject(it).getString("module") }

    @Test
    fun `the official module list is exactly what the inventory recorded`() {
        val names = moduleNames()

        assertEquals("module count drifted; regenerate the inventory deliberately", 50, names.size)
        assertEquals("module ids must stay unique", names.size, names.toSet().size)
    }

    @Test
    fun `nothing official is missing without being recorded`() {
        val rows = (0 until modules().length()).map { modules().getJSONObject(it) }
        val missing = rows
            .filter { it.getString("status") == "missing" && it.getBoolean("official") }
            .map { it.getString("module") }
            .toSet()

        assertEquals(
            "an official module lost its implementation, or a new one appeared: $missing",
            knownMissingOfficial,
            missing,
        )
    }

    @Test
    fun `screens the official client does not have are marked as ours`() {
        val rows = (0 until modules().length()).map { modules().getJSONObject(it) }
        val oursOnly = rows.filterNot { it.getBoolean("official") }.map { it.getString("module") }.toSet()

        assertEquals(
            "the set of our own, non-official screens changed: $oursOnly",
            knownOursOnly,
            oursOnly,
        )
    }

    @Test
    fun `the plan declares exactly the modules the inventory lists`() {
        val plan = File("../../docs/official-parity-plan.md")
        assertTrue("the parity plan is missing", plan.exists())
        val text = plan.readText()
        assertTrue("plan looks truncated", text.length > 4_000)

        // The plan ends with a generated marker block listing every module id.
        // A module the official client gained and we never noticed would show up
        // here as a mismatch.
        val declared = Regex("<!--\\s*parity:modules.*?<!--\\s*(.*?)\\s*-->", RegexOption.DOT_MATCHES_ALL)
            .find(text)
            ?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }

        assertTrue("the plan has no parity:modules marker block", declared != null)
        assertEquals(
            "the plan and the inventory disagree about which modules exist",
            moduleNames().sorted(),
            declared!!.sorted(),
        )
    }

    @Test
    fun `every module carries a label, an area and an official class count`() {
        val rows = (0 until modules().length()).map { modules().getJSONObject(it) }

        rows.forEach { row ->
            assertTrue("${row.getString("module")} has no area", row.getString("area").isNotBlank())
            assertTrue("${row.getString("module")} has no label", row.getString("label").isNotBlank())
            val classes = row.getInt("official_css_classes")
            val mentions = row.getInt("official_mentions")
            assertTrue(
                "${row.getString("module")} contradicts itself: official=$classes classes, $mentions mentions",
                row.getBoolean("official") == (classes > 0 || mentions >= 5),
            )
        }
    }

    @Test
    fun `the plan keeps the four phases that carry the most official weight`() {
        val plan = File("../../docs/official-parity-plan.md").readText()

        listOf("P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9").forEach { phase ->
            assertTrue("phase $phase disappeared from the plan", plan.contains("### $phase"))
        }
        assertTrue("the plan must keep its cannot-do evidence table", plan.contains("share_app_device_required"))
        assertTrue("the plan must keep the no-text-rewrite rule", plan.contains("禁止用文本区间整块改写 Kotlin"))
    }
}
