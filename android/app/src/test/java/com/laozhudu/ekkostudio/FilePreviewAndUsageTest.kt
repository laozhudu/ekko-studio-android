package com.laozhudu.ekkostudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * P3c + P3d: previewing a file before downloading it, and the cache ring.
 */
class FilePreviewAndUsageTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HermesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HermesApi(server.url("/").toString().trimEnd('/'), "saved-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json"))
    }

    @Test
    fun `text and image files preview, everything else downloads`() {
        assertEquals(FilePreviewKind.Text, previewKindOf("notes.md"))
        assertEquals(FilePreviewKind.Text, previewKindOf("server.LOG"))
        assertEquals(FilePreviewKind.Text, previewKindOf("data.json"))
        assertEquals(FilePreviewKind.Image, previewKindOf("shot.png"))
        assertEquals(FilePreviewKind.Image, previewKindOf("photo.JPEG"))
        assertEquals(FilePreviewKind.Download, previewKindOf("archive.zip"))
        assertEquals(FilePreviewKind.Download, previewKindOf("report.pdf"))
    }

    @Test
    fun `a file with no extension downloads rather than pretending to be text`() {
        assertEquals(FilePreviewKind.Download, previewKindOf("Makefile2"))
        assertEquals("", extensionOf("Makefile2"))
    }

    @Test
    fun `the previewer truncates to keep a huge log from freezing the transcript`() {
        assertTrue("a character cap must exist", PREVIEW_CHAR_LIMIT in 10_000..500_000)
        val dialog = File("src/main/java/com/laozhudu/ekkostudio/FilePreviewDialog.kt").readText()
        assertTrue("the cap must actually be applied", dialog.contains("takeLast(PREVIEW_CHAR_LIMIT)"))
    }

    @Test
    fun `opening a file card previews instead of downloading straight away`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

        assertTrue(activity.contains("onDownload = { file -> previewFile = file }"))
        assertTrue("the download button stays available", activity.contains("viewModel.downloadChatFile(file"))
    }

    @Test
    fun `cache hit rate counts cache reads against the whole prompt`() {
        val stats = UsageStats(
            inputTokens = 96_867_954,
            outputTokens = 1_000,
            cacheReadTokens = 4_354_466_256,
            cacheWriteTokens = 0,
            reasoningTokens = 0,
            sessions = 40,
            cost = 17.0,
            models = emptyList(),
            agents = emptyList(),
            daily = emptyList(),
        )

        assertEquals(0.978, stats.cacheHitRate!!, 0.001)
    }

    @Test
    fun `cache hit rate is null when nothing was sent`() {
        val stats = UsageStats(0, 0, 0, 0, 0, 0, 0.0, emptyList(), emptyList(), emptyList())

        assertNull(stats.cacheHitRate)
    }

    @Test
    fun `the account filter is the verified query parameter`() {
        enqueue(
            """{"total_input_tokens":10,"total_output_tokens":2,"total_sessions":1,"total_cost":0.5,
                "account_attribution_available":true,
                "filter_options":{"accounts":[{"id":"1","username":"admin"},{"id":"2","username":"guest"}]},
                "model_usage":[],"agent_usage":[],"daily_usage":[]}""",
        )

        val stats = api.usageStats(30, account = "2")

        val request = server.takeRequest()
        // Confirmed against the live server: an unknown id returns zeroes, so the
        // parameter is honoured rather than ignored.
        assertEquals("/api/studio/usage/stats?days=30&account=2", request.path)
        assertEquals(2, stats.accounts.size)
        assertEquals("guest", stats.accounts[1].username)
        assertTrue(stats.accountAttribution)
    }

    @Test
    fun `no account filter sends no account parameter`() {
        enqueue("""{"total_input_tokens":1,"model_usage":[],"agent_usage":[],"daily_usage":[]}""")

        api.usageStats(7)

        assertEquals("/api/studio/usage/stats?days=7", server.takeRequest().path)
    }

    @Test
    fun `the ring draws a proportion, not a bar with no end`() {
        val ring = File("src/main/java/com/laozhudu/ekkostudio/UsageCacheRing.kt").readText()

        assertTrue(ring.contains("drawArc("))
        assertTrue("100dp with an 8dp stroke, per the stylesheet", ring.contains("size(100.dp)"))
        assertTrue(ring.contains("stroke = 8.dp.toPx()"))
        // An unknown rate must not read as 0%.
        assertTrue(ring.contains("if (rate == null)"))
    }

    @Test
    fun `the account row hides itself when there is only one account`() {
        val ring = File("src/main/java/com/laozhudu/ekkostudio/UsageCacheRing.kt").readText()

        assertTrue(ring.contains("if (accounts.size <= 1) return"))
    }
}
