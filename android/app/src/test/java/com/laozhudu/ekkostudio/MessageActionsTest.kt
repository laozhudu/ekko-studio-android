package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3a: the three actions the official client puts under every bubble.
 *
 * From the shipped bundle's `.message-meta`: speech (assistant only), copy, and
 * reference, each a 24dp button holding a 14dp icon, sitting in a 24dp row with
 * a 4dp top margin. The clock comes after them, in the same row.
 */
class MessageActionsTest {
    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
    private val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
    private val metrics = File("src/main/java/com/laozhudu/ekkostudio/ChatMetrics.kt").readText()

    private val bubble: String
        get() = KotlinSource.funBody(activity, "MessageBubble") ?: error("MessageBubble is gone")

    @Test
    fun `the meta row holds 24dp buttons with 14dp icons in the official order`() {
        assertEquals("24dp button", true, metrics.contains("val metaButton = 24.dp"))
        assertEquals("6dp radius", true, metrics.contains("val metaButtonRadius = 6.dp"))
        assertEquals("24dp row", true, metrics.contains("val metaRowMinHeight = 24.dp"))
        assertEquals("11sp clock", true, metrics.contains("val metaTimeSize = 11.sp"))

        // Order in the source: speech, copy, reference, then the clock.
        val speech = bubble.indexOf("onSpeak?.let")
        val copy = bubble.indexOf("onCopy?.let")
        val quote = bubble.indexOf("onQuote?.let")
        val clock = bubble.indexOf("Text(\n                        stamp,")
        assertTrue("speech button missing", speech > 0)
        assertTrue("copy must follow speech", copy > speech)
        assertTrue("reference must follow copy", quote > copy)
        assertTrue("clock must come after the buttons", clock > quote)
    }

    @Test
    fun `read aloud is offered only for assistant text`() {
        val callSite = activity.substringAfter("MessageBubble(")

        assertTrue(
            "speech must be suppressed on the user's own messages",
            callSite.contains("onSpeak = if (!line.fromUser && line.text.isNotBlank())"),
        )
    }

    @Test
    fun `speaking is tracked per message, not per app`() {
        assertTrue(viewModel.contains("val speakingMessageId: String? = null"))
        assertTrue(KotlinSource.hasFun(viewModel, "speakLine"))

        val speakLine = KotlinSource.funBody(viewModel, "speakLine")!!
        // Tapping the bubble that is already playing stops it.
        assertTrue(speakLine.contains("_state.value.speakingMessageId == id"))
        assertTrue(speakLine.contains("stopSpeaking()"))

        // Every terminal path clears the id, or the pause icon sticks forever.
        val speak = KotlinSource.funBody(viewModel, "speak")!!
        assertEquals(
            "speaking=false must always be paired with clearing the id",
            speak.split("speaking = false").size - 1,
            speak.split("speakingMessageId = null").size - 1,
        )
    }

    @Test
    fun `the active button uses the official selected and pressed colours`() {
        val button = KotlinSource.funBody(activity, "MetaButton")!!

        assertTrue("active fill", button.contains("inkSelectedBg(inkDark)"))
        assertTrue("idle fill is transparent", button.contains("Color.Transparent"))
        assertTrue("icon is 14dp", button.contains("size(14.dp)"))
    }

    @Test
    fun `copying gives feedback, because the clipboard does not`() {
        assertTrue(KotlinSource.hasFun(viewModel, "notifyCopied"))
        assertTrue(activity.contains("viewModel.notifyCopied()"))
    }

    @Test
    fun `the long-press sheet survives the new inline buttons`() {
        // The inline row covers the three common actions; select-text and fork are
        // still only reachable from the sheet, so it must stay.
        assertTrue(activity.contains("message_select_text"))
        assertTrue(activity.contains("message_fork"))
        assertTrue(bubble.contains("onActions"))
    }

    @Test
    fun `every new string exists in all three locales`() {
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            listOf("message_speak", "message_copied").forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the bubble still does not submit anything by itself`() {
        // Regression guard: adding interactive children to a combinedClickable
        // parent is how a copy button ends up sending the message.
        assertFalse(bubble.contains("onSend"))
        assertTrue(bubble.contains("onClick = {}"))
    }
}
