package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two failures that made the app feel broken:
 *
 * 1. Approvals never appeared. The dialog lived inside ConversationScreen, so a
 *    run that asked while the user was elsewhere showed nothing; a background
 *    run's event was dropped outright. The host now sits at the app root, and the
 *    write gate — which announcements nothing on the socket — is polled so a
 *    blocked tool call is visible.
 * 2. The UI looked dead after switching conversations, because the composer was
 *    gated on the app-wide `sending` flag rather than the conversation's own run.
 */
class ApprovalAndBusyStateTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
    private val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

    @Test
    fun approvalHostIsMountedAtTheAppRoot() {
        val root = activity.substringAfter("private fun App(").substringBefore("private fun ApprovalBanner(")
        assertTrue("host is rendered above every screen", root.contains("RunActionHost(state, viewModel)"))
        assertTrue("the dialog is its own composable", activity.contains("private fun RunActionHost("))
        // It must not live inside the transcript any more.
        val conversation = activity.substringAfter("private fun ConversationScreen(")
            .substringBefore("private fun ConversationTopBar(")
        assertFalse(conversation.contains("R.string.run_approval_title"))
    }

    @Test
    fun backgroundApprovalsAreRecordedInsteadOfDropped() {
        val collector = viewModel.substringAfter("is RunEvent.RequiresAction ->")
        assertTrue(
            "the event is stored even when its session is off screen",
            collector.contains("Always recorded"),
        )
    }

    @Test
    fun writeGateIsPolledAndSurfacedAnywhere() {
        assertTrue("quiet poller exists", viewModel.contains("fun pollWriteGate()"))
        assertTrue("polled on a timer", activity.contains("viewModel.pollWriteGate()"))
        assertTrue("banner exists", activity.contains("private fun ApprovalBanner("))
        assertTrue(
            "banner is pinned above the transcript",
            activity.contains("state.writeGateRecords.isNotEmpty() && state.screen != Screen.WriteGate"),
        )
    }

    @Test
    fun composerIsNotBlockedByAnotherConversationsRun() {
        val send = viewModel.substringAfter("fun send(message: String, replyTo: String? = null) {").substringBefore("_state.update {")
        assertTrue("busy is scoped to this conversation", send.contains("val busyHere = activeRunSessionId != null"))
        assertFalse(
            "the app-wide flag must not gate sending",
            send.contains("|| _state.value.sending"),
        )
        assertFalse(
            "UI does not gate controls on the global flag",
            activity.contains("enabled = !state.sending"),
        )
        assertTrue(
            "the send button only becomes Stop for this conversation's run",
            activity.contains("val runHere = state.runSessionId != null && state.runSessionId == state.openSession?.id"),
        )
    }
}
