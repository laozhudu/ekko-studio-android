package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Leaving a conversation must not stop its run, and coming back must restore the
 * live status. The original bug: `openSession` called `cancelActiveRun()` on the
 * way in, which closed the socket, so the run's events stopped arriving and the
 * status strip vanished on return.
 */
class RunSurvivesNavigationTest {

    private val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

    private val openSession: String
        get() = viewModel.substringAfter("fun openSession(session: SessionSummary) {")
            .substringBefore("historyJob?.cancel()")

    @Test
    fun openingAnotherConversationDoesNotCancelTheRun() {
        assertTrue(
            "the run's own session must re-attach instead of cancelling",
            openSession.contains("activeRunSessionId == session.id -> attachLiveRun()"),
        )
        assertTrue(
            "a run in another session is left alone",
            openSession.contains("activeRunSessionId != null ->"),
        )
        // The cancel is only reached when nothing is running.
        assertTrue(openSession.contains("else -> cancelActiveRun(abort = false)"))
        assertFalse(
            "cancelActiveRun must not run unconditionally on open",
            openSession.trimStart().startsWith("cancelActiveRun"),
        )
    }

    @Test
    fun returningRestoresTheStatusAndPartialAnswer() {
        val attach = viewModel.substringAfter("private fun attachLiveRun()")
            .substringBefore("private fun finishRun(")
        assertTrue("status is restored", attach.contains("runStatus = \"running\""))
        assertTrue("elapsed clock is restored", attach.contains("runStartedAtMillis = runStartedAtMillis"))
        assertTrue("partial answer is replayed", attach.contains("runAnswer.toString()"))
        assertTrue("replay is idempotent", attach.contains("filterNot { it.streaming && !it.fromUser }"))
    }

    @Test
    fun backgroundRunEventsDoNotPaintOverAnotherConversation() {
        assertTrue("collector gates UI writes", viewModel.contains("fun onScreen(): Boolean ="))
        assertTrue(
            "text deltas only touch the screen when the run's session is open",
            viewModel.contains("is RunEvent.Text -> {") &&
                viewModel.contains("if (onScreen()) {"),
        )
    }

    @Test
    fun finishedBackgroundRunReloadsTheTranscript() {
        val finish = viewModel.substringAfter("private fun finishRun(sessionId: String)")
            .substringBefore("private class SocketUnavailable")
        assertTrue("transcript is refreshed from the server", finish.contains("refreshConversation()"))
        assertTrue("status is cleared", finish.contains("runStatus = \"\""))
    }
}
