package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the chat look to the official client's own numbers.
 *
 * The values in [ChatMetrics] were read out of the stylesheet shipped inside the
 * official 1.0.8 APK. This test does not prove the app *renders* them (that needs
 * a screen), it proves nobody quietly changes one of them back, and that the
 * structural rules the official client relies on are still in place:
 *
 *  - both roles share one bubble surface, separated by alignment;
 *  - the clock sits outside the bubble;
 *  - the session row is one title line plus one preview line, no big avatar;
 *  - the conversation header is a 46dp bar with a centred title.
 */
class OfficialChatAnatomyTest {
    private val main = File("src/main/java/com/laozhudu/ekkostudio")
    private fun read(name: String) = File(main, name).readText()

    @Test
    fun `session row uses the official compact anatomy`() {
        val metrics = read("ChatMetrics.kt")
        assertEquals("13sp title", true, metrics.contains("val sessionTitleSize = 13.sp"))
        assertEquals("18sp line height", true, metrics.contains("val sessionTitleLineHeight = 18.sp"))
        assertEquals("550 weight", true, metrics.contains("val sessionTitleWeight = 550"))
        assertEquals("16dp agent logo", true, metrics.contains("val sessionAgentLogo = 16.dp"))
        assertEquals("2dp running track", true, metrics.contains("val sessionRunningTrack = 2.dp"))
        assertEquals("8/10 row padding", true, metrics.contains("val sessionRowPaddingH = 10.dp"))
    }

    @Test
    fun `the session row is one line of title and one line of preview, with no portrait`() {
        val activity = read("MainActivity.kt")
        val start = activity.indexOf("private fun SessionRow(")
        val end = activity.indexOf("private fun SessionRunningBar(")
        assertTrue("SessionRow must exist", start in 0 until end)
        val row = activity.substring(start, end)

        assertTrue("title is a single line", row.contains("maxLines = 1"))
        assertTrue(
            "the row draws the 16dp agent badge, not a 42dp portrait",
            row.contains("size = ChatMetrics.sessionAgentLogo") && !row.contains("size = 42.dp"),
        )
        assertTrue("the row no longer carries a navigation chevron", !row.contains("KeyboardArrowRight"))
    }

    @Test
    fun `both roles share one bubble surface and one radius`() {
        val activity = read("MainActivity.kt")
        val start = activity.indexOf("private fun MessageBubble(")
        val end = activity.indexOf("internal fun quoteForReply(")
        assertTrue(start in 0 until end)
        val bubble = activity.substring(start, end)

        assertTrue(
            "one bubble fill for both roles",
            bubble.contains("if (line.isError) inkErrorSoft(inkDark) else inkMessageBg(inkDark)"),
        )
        assertTrue("official 10dp radius", bubble.contains("RoundedCornerShape(ChatMetrics.bubbleRadius)"))
        assertTrue("10/14 padding", bubble.contains("ChatMetrics.bubblePaddingH"))
        assertTrue("no two-sided Telegram palette left", !bubble.contains("telegram"))
    }

    @Test
    fun `the clock lives under the bubble, not inside it`() {
        val activity = read("MainActivity.kt")
        val start = activity.indexOf("private fun MessageBubble(")
        val end = activity.indexOf("internal fun quoteForReply(")
        val bubble = activity.substring(start, end)

        val metaRow = bubble.indexOf("Official `.message-meta`")
        val cardClose = bubble.lastIndexOf("}\n            }", metaRow)
        assertTrue("meta row must be inside MessageBubble", metaRow > 0)
        // The meta row is drawn after the Card closes, i.e. outside the bubble.
        val cardIndex = bubble.indexOf("shape = RoundedCornerShape(ChatMetrics.bubbleRadius)")
        assertTrue("card is drawn before the meta row", cardIndex in 0 until metaRow)
        assertTrue("meta row sits below the card", cardClose > cardIndex)
        assertTrue("11sp clock", bubble.contains("ChatMetrics.metaTimeSize"))
    }

    @Test
    fun `the conversation header is the official 46dp centred bar`() {
        val activity = read("MainActivity.kt")
        val start = activity.indexOf("private fun ConversationTopBar(")
        assertTrue(start > 0)
        val header = activity.substring(start, start + 2600)

        assertTrue("46dp tall", header.contains("ChatMetrics.headerHeight"))
        assertTrue("14dp side padding", header.contains("ChatMetrics.headerPaddingH"))
        assertTrue("centred title", header.contains("TextAlign.Center"))
        assertTrue("15sp/650 title", header.contains("ChatMetrics.headerTitleSize") && header.contains("ChatMetrics.headerTitleWeight"))
        assertTrue("38dp buttons", header.contains("ChatMetrics.headerButton"))
    }

    @Test
    fun `message list uses the official 20dp gutter and 14dp rhythm`() {
        val activity = read("MainActivity.kt")
        assertTrue(activity.contains("start = 20.dp,"))
        assertTrue(activity.contains("verticalArrangement = Arrangement.spacedBy(ChatMetrics.messageGap)"))
        assertNotNull(ChatMetrics.listBottomGap)
    }

    @Test
    fun `the process strip keeps the official fixed heights`() {
        assertEquals(32, ChatMetrics.thinkingStripHeight.value.toInt())
        assertEquals(26, ChatMetrics.toolChipHeight.value.toInt())
        assertEquals(7, ChatMetrics.toolChipRadius.value.toInt())
        assertEquals(520, ChatMetrics.toolRunCardMaxWidth.value.toInt())
        assertEquals(30, ChatMetrics.toolRunHeaderMinHeight.value.toInt())
    }

    @Test
    fun `context meter keeps the official size and thresholds`() {
        assertEquals(34, ChatMetrics.contextMeterWidth.value.toInt())
        assertEquals(3, ChatMetrics.contextMeterHeight.value.toInt())
        assertEquals(60f, ChatMetrics.contextWarningPercent, 0.001f)
        assertEquals(80f, ChatMetrics.contextDangerPercent, 0.001f)
        assertEquals(0xFFD59A2D.toInt(), CONTEXT_WARNING_HEX.toInt())
    }
}
