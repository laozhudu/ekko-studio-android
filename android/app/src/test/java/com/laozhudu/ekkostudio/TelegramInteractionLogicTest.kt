package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Telegram's interaction model, not its paint job:
 *
 *  - a draft belongs to its chat; navigating away must not clear what you typed
 *  - a tap on a message does nothing; a long press opens the menu
 *  - dragging a message to the right quotes it in the composer
 *  - a reply is drawn as a quote block inside the bubble, not as "> text" welded
 *    into the answer
 *  - scrolling away from the bottom shows an unread counter, and returning to the
 *    bottom clears it
 */
class TelegramInteractionLogicTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
    private val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
    private val chatWidgets = File("src/main/java/com/laozhudu/ekkostudio/ChatWidgets.kt").readText()
    private val store = File("src/main/java/com/laozhudu/ekkostudio/Store.kt").readText()

    private val conversation: String
        get() = activity.substringAfter("private fun ConversationScreen(")
            .substringBefore("private fun ConversationTopBar(")

    @Test
    fun draftsArePerConversationAndPersisted() {
        assertTrue("store keeps one draft per chat", store.contains("fun draftFor(conversationId: String)"))
        assertTrue(store.contains("fun setDraftFor("))
        assertTrue(
            "the composer restores that chat's draft",
            conversation.contains("mutableStateOf(viewModel.draft(conversationKey))"),
        )
        assertTrue(
            "typing persists immediately",
            conversation.contains("viewModel.saveDraft(conversationKey, it)"),
        )
        assertTrue(
            "sending clears it",
            conversation.contains("viewModel.saveDraft(conversationKey, \"\")"),
        )
    }

    @Test
    fun tapIsSilentAndLongPressOpensTheMenu() {
        val bubble = activity.substringAfter("private fun MessageBubble(")
            .substringBefore("private fun quoteForReply(")
        assertTrue(bubble.contains("onClick = {}"))
        assertTrue(bubble.contains("onLongClick = { onActions?.invoke() }"))
    }

    @Test
    fun swipingAMessageQuotesIt() {
        assertTrue(chatWidgets.contains("SwipeToReply("))
        assertTrue(activity.contains("detectHorizontalDragGestures("))
        assertTrue(
            "the gesture is wrapped around each bubble",
            conversation.contains("SwipeToReply(enabled = true, onReply = { replyingTo = line })"),
        )
    }

    @Test
    fun theQuoteTravelsAsItsOwnField() {
        assertTrue(viewModel.contains("fun send(message: String, replyTo: String? = null)"))
        assertTrue(viewModel.contains("replyToNote = replyTo?.take(400)"))
        assertTrue(
            "the agent still receives the quote in the payload",
            viewModel.contains("input = replyTo?.let { quoteForReply(it, text) } ?: text"),
        )
        assertTrue("rendered as a block", activity.contains("line.replyToNote?.takeIf"))
    }

    @Test
    fun unreadCountTracksTheBottom() {
        assertTrue(store.contains("fun readIndexFor("))
        assertTrue(conversation.contains("viewModel.markRead(conversationKey, state.lines.size)"))
        assertTrue(
            "the pill shows the count",
            conversation.contains("if (unread > 99) \"99+\" else unread.toString()"),
        )
    }
}
