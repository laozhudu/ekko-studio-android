package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the transcript readable. The screen must show one working indicator and
 * one collapsed process block per reply — the way Telegram keeps "typing…" in
 * the header and Feishu folds a bot's tool work into a single line.
 *
 * What this replaced: three separate working indicators (top strip, an
 * auto-expanded thinking panel, and a "thinking…" row under the list), plus a
 * clock on every fragment of a multi-part answer.
 */
class TranscriptQuietUiTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

    private val conversation: String
        get() = activity.substringAfter("private fun ConversationScreen(")
            .substringBefore("private fun ConversationTopBar(")

    @Test
    fun thereIsExactlyOneWorkingIndicator() {
        assertFalse(
            "the global status strip belongs in the header, not the transcript",
            conversation.contains("RunStatusStrip("),
        )
        assertFalse(
            "the duplicate 'thinking…' row under the list is gone",
            conversation.contains("conversation_thinking"),
        )
        assertTrue("the header carries it", activity.contains("state.runStatus == \"running\""))
    }

    @Test
    fun processBlockStartsCollapsedEvenWhileStreaming() {
        val block = activity.substringAfter("private fun ThinkingTimeline(")
            .substringBefore("private fun InkAgentBadge(")
        assertTrue(
            "expanding must be a deliberate tap",
            block.contains("var expanded by rememberSaveable(line.startedAtMillis) { mutableStateOf(false) }"),
        )
        assertFalse(
            "streaming must not auto-expand the panel",
            block.contains("?: line.streaming") || block.contains("{ mutableStateOf(line.streaming) }"),
        )
    }

    @Test
    fun processBlockFoldsToolsNoticesAndReasoningIntoOneSurface() {
        val block = activity.substringAfter("private fun ThinkingTimeline(")
            .substringBefore("private fun InkAgentBadge(")
        assertTrue("one container", block.contains("Surface("))
        assertTrue("tools inside it", block.contains("line.tools.forEach"))
        assertTrue("lifecycle notices inside it", block.contains("notices.forEach"))
        assertTrue("reasoning inside it", block.contains("line.reasoning?.takeIf"))
    }

    @Test
    fun authorAndClockAppearOncePerTurn() {
        assertTrue(
            "the list decides grouping from neighbours",
            conversation.contains("items = state.lines,"),
        )
        assertTrue(
            "author only on the first assistant fragment",
            conversation.contains("showAuthor = !line.fromUser && !sameSender"),
        )
        assertTrue(
            "clock only on the last fragment",
            conversation.contains("showStamp = next == null || next.fromUser ||"),
        )
    }
}
