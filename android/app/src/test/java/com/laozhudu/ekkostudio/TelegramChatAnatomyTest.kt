package com.laozhudu.ekkostudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The chat follows Telegram's anatomy.
 *
 * What that means concretely, and is asserted here:
 *  - the two sides are told apart by colour (tinted outgoing, flat incoming),
 *    not by alignment alone
 *  - the bubble has a pinched corner on the sender's own bottom edge
 *  - the clock lives inside the bubble, bottom-right, once per turn
 *  - a day pill separates dates
 *  - an approval is an inline keyboard attached to the message, not a dialog
 *    that covers the conversation
 */
class TelegramChatAnatomyTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
    // Chat widgets live in their own file since the split; assertions about
    // them must not depend on which file they sit in.
    private val chatWidgets = File("src/main/java/com/laozhudu/ekkostudio/ChatWidgets.kt").readText()
    private val theme = File("src/main/java/com/laozhudu/ekkostudio/Theme.kt").readText()

    @Test
    fun sidesDifferByColour() {
        assertTrue(theme.contains("fun telegramOutgoingBg("))
        assertTrue(theme.contains("fun telegramIncomingBg("))
        assertTrue(
            "the bubble picks a side's colour",
            activity.contains("isUser -> telegramOutgoingBg(inkDark)") &&
                activity.contains("else -> telegramIncomingBg(inkDark)"),
        )
    }

    @Test
    fun bubbleHasAPinchedTailCorner() {
        assertTrue(theme.contains("fun telegramBubbleShape(isUser: Boolean)"))
        assertTrue("outgoing pinches bottom-end", theme.contains("bottomEnd = if (isUser) 4.dp else 16.dp"))
        assertTrue("incoming pinches bottom-start", theme.contains("bottomStart = if (isUser) 16.dp else 4.dp"))
        assertTrue(activity.contains("shape = telegramBubbleShape(isUser)"))
    }

    @Test
    fun clockIsInsideTheBubbleAndOncePerTurn() {
        val bubble = activity.substringAfter("private fun MessageBubble(")
            .substringBefore("private fun quoteForReply(")
        assertTrue("meta row is part of the bubble column", bubble.contains("telegramMetaText(isUser, inkDark)"))
        assertTrue("state glyph for the agent side", bubble.contains("Icons.Filled.Schedule"))
        assertFalse("no floating stamp under the bubble", bubble.contains("HStudio message-meta"))
    }

    @Test
    fun datesAreSeparatedByAPill() {
        assertTrue(chatWidgets.contains("DaySeparator("))
        assertTrue("inserted when the day changes", activity.contains("if (dayKeyOf(previous?.timestamp) != dayKeyOf(line.timestamp))"))
    }

    @Test
    fun approvalsUseAnInlineKeyboard() {
        assertTrue(chatWidgets.contains("InlineActionKeyboard("))
        assertTrue(
            "rendered as a list item under the request",
            activity.contains("state.pendingRunAction") && activity.contains("InlineActionKeyboard("),
        )
        assertTrue(
            "the dialog steps aside for the inline keyboard",
            activity.contains("if (answeredInline) return"),
        )
    }
}
