package com.laozhudu.ekkostudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The three group surfaces that were still missing: agent avatars, the room's own
 * workspace files, and remote agent connectors.
 */
class GroupExtrasTest {
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

    // ── avatars ──────────────────────────────────────────────────────────

    @Test
    fun `a generated avatar carries only a seed`() {
        val raw = AgentAvatar.generated("agent-123")

        val parsed = JSONObject(raw)
        assertEquals("generated", parsed.getString("type"))
        assertEquals("agent-123", parsed.getString("seed"))
        assertFalse("no bytes should ride along", parsed.has("dataUrl"))
    }

    @Test
    fun `an uploaded avatar is a png jpeg or webp data url`() {
        val raw = AgentAvatar.image("image/png", byteArrayOf(1, 2, 3))!!

        val parsed = JSONObject(raw)
        assertEquals("image", parsed.getString("type"))
        assertTrue(parsed.getString("dataUrl").startsWith("data:image/png;base64,"))
    }

    @Test
    fun `the formats the server rejects are rejected here too`() {
        assertNull("gif is not in the validator", AgentAvatar.image("image/gif", byteArrayOf(1)))
        assertNull(AgentAvatar.image("image/svg+xml", byteArrayOf(1)))
        assertNull("an empty file is not a picture", AgentAvatar.image("image/png", ByteArray(0)))
    }

    @Test
    fun `an oversized photo is refused before it is encoded`() {
        val tooBig = ByteArray(AgentAvatar.MAX_IMAGE_BYTES + 1)

        assertNull(AgentAvatar.image("image/jpeg", tooBig))
    }

    @Test
    fun `parse reads back both shapes and ignores junk`() {
        val generated = AgentAvatar.parse(AgentAvatar.generated("seed-1"))!!
        assertEquals("generated", generated.type)
        assertEquals("seed-1", generated.seed)

        val image = AgentAvatar.parse(AgentAvatar.image("image/webp", byteArrayOf(9))!!)!!
        assertEquals("image", image.type)
        assertTrue(image.dataUrl!!.startsWith("data:image/webp"))

        assertNull(AgentAvatar.parse(""))
        assertNull(AgentAvatar.parse("not json"))
        assertNull(AgentAvatar.parse("""{"type":"unknown"}"""))
    }

    // ── room workspace files ─────────────────────────────────────────────

    @Test
    fun `the listing keeps the fields the browser needs`() {
        enqueue(
            """{"entries":[
                 {"name":"notes.md","path":"docs/notes.md","isDir":false,"size":42,"modTime":"2026-10-21T10:00:00Z"},
                 {"name":"docs","path":"docs","isDir":true,"size":0,"modTime":"2026-10-21T09:00:00Z"}],
               "path":"docs","absolutePath":"/tmp/room/docs"}""",
        )

        val listing = api.roomWorkspaceListing("r1", "docs")

        assertEquals("docs", listing.path)
        assertEquals(2, listing.entries.size)
        assertEquals(42L, listing.entries.first().size)
        assertTrue(listing.entries[1].isDir)
        assertEquals("/api/studio/group-chat/rooms/r1/workspace-files/list?path=docs", server.takeRequest().path)
    }

    @Test
    fun `read and write use a plain string body, not an object`() {
        enqueue("""{"content":"hello","path":"a.txt","size":5}""")
        assertEquals("hello", api.roomWorkspaceRead("r1", "a.txt"))

        enqueue("""{"ok":true,"path":"a.txt"}""")
        api.roomWorkspaceWrite("r1", "a.txt", "updated")

        server.takeRequest()
        val write = server.takeRequest()
        assertEquals("PUT", write.method)
        assertEquals("/api/studio/group-chat/rooms/r1/workspace-file/write", write.path)
        val body = JSONObject(write.body.readUtf8())
        assertEquals("updated", body.getString("content"))
        assertTrue("content must be a string", body.get("content") is String)
    }

    @Test
    fun `mkdir rename copy and delete send the parameter names the server reads`() {
        enqueue("""{"ok":true}""")
        api.roomWorkspaceMkdir("r1", "newdir")
        assertEquals("""{"path":"newdir"}""", server.takeRequest().body.readUtf8())

        enqueue("""{"ok":true}""")
        api.roomWorkspaceRename("r1", "old.txt", "new.txt")
        val rename = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("old.txt", rename.getString("oldPath"))
        assertEquals("new.txt", rename.getString("newPath"))

        enqueue("""{"ok":true}""")
        api.roomWorkspaceCopy("r1", "a.txt", "b.txt")
        val copy = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("a.txt", copy.getString("srcPath"))
        assertEquals("b.txt", copy.getString("destPath"))

        enqueue("""{"ok":true}""")
        api.roomWorkspaceDelete("r1", "newdir", recursive = true)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        val body = JSONObject(delete.body.readUtf8())
        assertTrue("a directory needs recursive, or the server refuses", body.getBoolean("recursive"))
    }

    // ── remote connectors ────────────────────────────────────────────────

    @Test
    fun `capabilities report what the handshake supports`() {
        enqueue(
            """{"service":"hermes-group-chat-link","protocolVersion":2,
                "authorizationPath":"/?groupChatAgentLink=1#/group-chat-link",
                "supportsManualPairingCode":true,"supportsOutboundRelay":true}""",
        )

        val caps = api.groupLinkCapabilities()

        assertEquals(2, caps.protocolVersion)
        assertTrue(caps.supportsManualPairingCode)
        assertTrue(caps.supportsOutboundRelay)
    }

    @Test
    fun `an empty connection list is normal, not an error`() {
        enqueue("""{"connections":[]}""")

        assertTrue(api.groupLinkConnections().isEmpty())
        assertEquals("/api/studio/group-chat-link/v1/connections", server.takeRequest().path)
    }

    @Test
    fun `disconnect names the connector`() {
        enqueue("""{"ok":true}""")

        api.disconnectGroupLink("conn-1")

        val request = server.takeRequest()
        assertEquals("/api/studio/group-chat-link/v1/disconnect", request.path)
        assertEquals("conn-1", JSONObject(request.body.readUtf8()).getString("connectorId"))
    }

    @Test
    fun `a join decision sends approved as a string, which is how the schema types it`() {
        enqueue("""{"ok":true}""")

        api.decideAgentLinkRequest("r1", "req-1", approved = true)

        val request = server.takeRequest()
        assertEquals("/api/studio/group-chat/rooms/r1/agent-link-requests/req-1/decision", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("true", body.getString("approved"))
        assertTrue("a boolean would be rejected", body.get("approved") is String)
    }

    @Test
    fun `pending join requests parse`() {
        enqueue(
            """{"requests":[{"requestId":"req-1","agentName":"Reviewer","origin":"192.168.1.9","requestedAt":"2026-10-21"}]}""",
        )

        val request = api.roomAgentLinkRequests("r1").single()

        assertEquals("req-1", request.id)
        assertEquals("Reviewer", request.agentName)
        assertEquals("192.168.1.9", request.origin)
    }

    @Test
    fun `every new string exists in all three locales`() {
        val keys = listOf(
            "room_tab_remote", "room_agent_avatar", "room_agent_avatar_random",
            "room_files_title", "room_files_new_folder", "room_remote_requests",
        )
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = java.io.File("src/main/res/$locale/strings.xml").readText()
            keys.forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
