package com.laozhudu.ekkostudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Live MCP tool inventory.
 *
 * `GET /api/hermes/mcp/tools` answers
 * `{"ok":true,"results":[{"server":…,"tools":[{"name":…}]}]}` on 0.7.38. It reports
 * what a server exposes at runtime, which can differ from the tools saved in the
 * config the editor shows.
 */
class McpToolsContractTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HermesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HermesApi(server.url("/").toString(), "token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun toolsAreGroupedByServer() {
        val browser = JSONObject()
            .put("server", "ekko-studio-browser")
            .put("tools", org.json.JSONArray())
        val use = JSONObject()
            .put("server", "ekko-studio-use")
            .put(
                "tools",
                org.json.JSONArray()
                    .put(JSONObject().put("name", "ekko_studio_use_toolset"))
                    .put(JSONObject().put("name", "ekko_studio_use_tool")),
            )
        server.enqueue(
            MockResponse().setBody(
                JSONObject().put("ok", true).put("results", org.json.JSONArray().put(use).put(browser)).toString(),
            ),
        )

        val tools = api.mcpTools()

        assertEquals(2, tools.size)
        // Sorted by server name so the dialog is stable between refreshes.
        assertEquals("ekko-studio-browser", tools[0].server)
        assertTrue("a server with no tools is still reported", tools[0].tools.isEmpty())
        assertEquals(
            listOf("ekko_studio_use_toolset", "ekko_studio_use_tool"),
            tools[1].tools,
        )
        assertEquals("/api/hermes/mcp/tools", server.takeRequest().path)
    }

    @Test
    fun plainStringToolsAreAccepted() {
        server.enqueue(
            MockResponse().setBody(
                """{"ok":true,"results":[{"server":"files","tools":["read","write"]}]}""",
            ),
        )

        assertEquals(listOf("read", "write"), api.mcpTools().single().tools)
    }

    @Test
    fun aMalformedResponseYieldsNoServersInsteadOfThrowing() {
        server.enqueue(MockResponse().setBody("""{"ok":false}"""))
        assertTrue(api.mcpTools().isEmpty())
    }
}
