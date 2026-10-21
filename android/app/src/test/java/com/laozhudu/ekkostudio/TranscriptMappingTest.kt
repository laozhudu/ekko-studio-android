package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transcript is built from server rows, and the mapping decides what the user
 * actually sees. Verified shapes come from a live 0.7.38 conversation:
 *
 *   {"role":"user","content":"…"}
 *   {"role":"assistant","content":"…","reasoning_content":"…"}
 *   {"role":"tool","tool_name":"terminal","content":"{\"output\":…}"}
 *   {"role":"command","content":"Branched session …"}
 */
class TranscriptMappingTest {

    @Test
    fun thinkingIsRestoredSoTheFoldSurvivesAReload() {
        val lines = messagesToLines(
            listOf(
                Message("1", "user", "question", "100"),
                Message("2", "assistant", "answer", "101", reasoning = "step by step"),
            ),
        )

        assertEquals(2, lines.size)
        assertEquals("step by step", lines[1].reasoning)
        assertEquals("answer", lines[1].text)
    }

    @Test
    fun anEmptyThinkingFieldDoesNotRenderAnEmptyFold() {
        val lines = messagesToLines(listOf(Message("1", "assistant", "answer", "101", reasoning = "  ")))
        assertNull(lines.single().reasoning)
    }

    @Test
    fun toolRowsBecomeStepsOnTheReplyRatherThanBubbles() {
        val lines = messagesToLines(
            listOf(
                Message("1", "user", "run the tests", "100"),
                Message("2", "tool", "{\"output\":\"PASS\"}", "101", toolName = "terminal"),
                Message("3", "tool", "{\"output\":\"ok\"}", "102", toolName = "write_file"),
                Message("4", "assistant", "done", "103", reasoning = "planned first"),
            ),
        )

        // user + one reply; the two tool rows are folded, not spoken.
        assertEquals(2, lines.size)
        assertEquals(2, lines[1].tools.size)
        assertEquals(listOf("terminal", "write_file"), lines[1].tools.map { it.name })
        assertTrue("folded steps are already finished", lines[1].tools.all { it.status == ToolRunStatus.Done })
        assertEquals("planned first", lines[1].reasoning)
    }

    @Test
    fun aToolRowWithNoNameStillRenders() {
        val lines = messagesToLines(
            listOf(
                Message("1", "tool", "{}", "100", toolName = ""),
                Message("2", "assistant", "ok", "101"),
            ),
        )
        assertEquals("tool", lines.single().tools.single().name)
    }

    @Test
    fun commandRowsStayOutOfTheConversation() {
        val lines = messagesToLines(
            listOf(
                Message("1", "command", "Branched session \"x\" from y.", "100"),
                Message("2", "user", "hi", "101"),
            ),
        )
        assertEquals(1, lines.size)
        assertEquals("hi", lines.single().text)
    }

    @Test
    fun toolRowsBeforeAnyReplyAreNotLostWhenTheReplyArrives() {
        val lines = messagesToLines(
            listOf(
                Message("1", "tool", "{}", "100", toolName = "terminal"),
                Message("2", "assistant", "answer", "101"),
                Message("3", "tool", "{}", "102", toolName = "write_file"),
                Message("4", "assistant", "second", "103"),
            ),
        )
        assertEquals(2, lines.size)
        assertEquals(1, lines[0].tools.size)
        assertEquals("terminal", lines[0].tools.single().name)
        assertEquals("write_file", lines[1].tools.single().name)
    }
}
