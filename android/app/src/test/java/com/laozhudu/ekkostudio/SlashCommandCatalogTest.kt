package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Slash commands are a closed set on the server: anything outside its allow-list
 * is silently dropped, so the app must not invent commands. These tests pin the
 * catalogue to that set and pin the tap behaviour to "insert, never send".
 */
class SlashCommandCatalogTest {

    private val catalog = File("src/main/java/com/laozhudu/ekkostudio/SlashCommands.kt").readText()
    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

    /** Copied verbatim from the server bundle: `kea=new Set([...])`. */
    private val serverCommands = setOf(
        "abort", "bundles", "clear", "compress", "create", "destroy", "fork", "goal",
        "learn", "moa", "plan", "queue", "reload-mcp", "reload-skills", "skill",
        "status", "steer", "subgoal", "title", "usage", "yolo",
    )

    @Test
    fun catalogueOnlyOffersCommandsTheServerAccepts() {
        val offered = Regex("SlashCommand\\(\"([a-z-]+)\"")
            .findAll(catalog)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("catalogue is empty", offered.isNotEmpty())
        val unknown = offered - serverCommands
        assertTrue("commands the server does not accept: $unknown", unknown.isEmpty())
    }

    @Test
    fun everyCommandHasAUsageThatStartsWithASlash() {
        val usages = Regex("SlashCommand\\(\"[a-z-]+\", \"([^\"]+)\"")
            .findAll(catalog)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(usages.isNotEmpty())
        usages.forEach { assertTrue("usage '$it' must start with /", it.startsWith("/")) }
    }

    @Test
    fun tappingACommandInsertsItInsteadOfSending() {
        val sheet = activity.substringAfter("ComposerSheet.SlashCommands ->")
            .substringBefore("null -> Unit")
        assertTrue("tapping must write into the draft", sheet.contains("onDraftChange("))
        assertFalse("tapping must not submit", sheet.contains("viewModel.send"))
    }
}
