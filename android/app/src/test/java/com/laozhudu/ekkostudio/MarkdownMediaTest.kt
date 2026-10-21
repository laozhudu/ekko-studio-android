package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P3b: images and bare links in a message.
 *
 * Before this, `![shot](url)` fell through to the paragraph parser and rendered as
 * the literal characters, and a message that was one long URL filled the screen
 * with an unbreakable string.
 */
class MarkdownMediaTest {
    private val blocks = parseChatMarkdown(
        """
        Here is the screenshot:

        ![login page](/api/studio/files/download?path=shot.png)

        https://example.com/a/very/long/path?with=query

        A sentence with https://example.com inside it stays text.
        """.trimIndent(),
    )

    @Test
    fun `a standalone image becomes an image block, not literal text`() {
        val image = blocks.filterIsInstance<ChatMarkdownBlock.Image>().single()

        assertEquals("login page", image.alt)
        assertEquals("/api/studio/files/download?path=shot.png", image.url)
    }

    @Test
    fun `an image with no alt text still parses`() {
        val image = parseChatMarkdown("![](/tmp/a.png)").filterIsInstance<ChatMarkdownBlock.Image>().single()

        assertEquals("", image.alt)
        assertEquals("/tmp/a.png", image.url)
    }

    @Test
    fun `a message that is only a url becomes a card`() {
        val card = blocks.filterIsInstance<ChatMarkdownBlock.LinkCard>().single()

        assertEquals("https://example.com/a/very/long/path?with=query", card.url)
    }

    @Test
    fun `a url inside a sentence stays part of the paragraph`() {
        val paragraphs = blocks.filterIsInstance<ChatMarkdownBlock.Paragraph>()

        assertTrue(
            "an inline URL must not be promoted to a card",
            paragraphs.any { it.text.contains("A sentence with https://example.com inside it") },
        )
        assertEquals("only one bare URL in the sample", 1, blocks.filterIsInstance<ChatMarkdownBlock.LinkCard>().size)
    }

    @Test
    fun `an image inside a code fence stays code`() {
        val code = parseChatMarkdown(
            """
            ```
            ![not an image](x.png)
            ```
            """.trimIndent(),
        ).filterIsInstance<ChatMarkdownBlock.Code>().single()

        assertTrue(code.text.contains("![not an image](x.png)"))
    }

    @Test
    fun `the host is what the card leads with`() {
        assertEquals("example.com", linkHost("https://example.com/a/b"))
        assertEquals("192.168.99.226:8648", linkHost("http://192.168.99.226:8648/api/x"))
        assertEquals("no-scheme.test", linkHost("no-scheme.test/path"))
    }

    @Test
    fun `images are fetched with the token, so the platform cannot load them directly`() {
        val api = File("src/main/java/com/laozhudu/ekkostudio/HermesApi.kt").readText()
        val fetch = KotlinSource.funBody(api, "fetchImageBytes")!!

        assertTrue("absolute urls pass through", fetch.contains("http://"))
        assertTrue("relative paths get the base url", fetch.contains("url(pathOrUrl)"))
        assertTrue("the token must ride along", fetch.contains("Authorization"))
    }

    @Test
    fun `the full-screen view is a real overlay`() {
        val media = File("src/main/java/com/laozhudu/ekkostudio/MarkdownMedia.kt").readText()

        // A Box inside the bubble would be clipped to the bubble's bounds.
        assertTrue(media.contains("Dialog("))
        assertTrue(media.contains("usePlatformDefaultWidth = false"))
        assertTrue(media.contains("onDismissRequest"))
    }

    @Test
    fun `a tall screenshot cannot take over the transcript`() {
        val media = File("src/main/java/com/laozhudu/ekkostudio/MarkdownMedia.kt").readText()

        assertTrue("inline images are capped", media.contains("heightIn(max = 260.dp)"))
    }

    @Test
    fun `image bytes are cached so scrolling does not refetch`() {
        val media = File("src/main/java/com/laozhudu/ekkostudio/MarkdownMedia.kt").readText()

        assertTrue(media.contains("decodedImages"))
        assertTrue("a failed load is remembered too", media.contains("decodedImages[url] = bitmap"))
    }
}
