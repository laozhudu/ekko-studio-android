package com.laozhudu.ekkostudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * P4: group members, group agents and room settings.
 *
 * All of this was already reachable — the room-detail response carries `members`,
 * `agents`, `inviteCode`, `canManage` and the handoff/summary fields, and the
 * routes exist — but every one of those fields was dropped by the parser, so the
 * screen could show a room and nothing else.
 */
class GroupRoomParityTest {
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

    private val roomJson = """
        {"room":{"id":"r1","name":"课题讨论","inviteCode":"EE4JKV","canManage":true,
          "workspace":"/root/.hermes-web-ui/group-chat/default/r1",
          "agentHandoffEnabled":true,"agentHandoffMaxDepth":2,"agentHandoffUnlimited":false,
          "summaryEveryTurns":20,"summaryModel":"m","summaryProvider":"p","summaryProfile":"default",
          "allowGuestAgents":false},
         "members":[{"userId":"u1","name":"admin","connectionStatus":"online"}],
         "agents":[{"id":"a1","agentId":"x","name":"Hermes","profile":"default","agent":"hermes",
                    "agentMode":"scoped","model":"deepseek","provider":"custom",
                    "reasoningEffort":"high","executorType":"server","connectionStatus":"online"}],
         "messages":[{"id":"m1","content":"hello","role":"user","senderName":"admin"}],
         "total":1,"hasMore":false}
    """.trimIndent()

    @Test
    fun `room detail keeps the members agents and settings it used to drop`() {
        enqueue(roomJson)

        val room = api.room("r1")

        assertEquals("EE4JKV", room.inviteCode)
        assertTrue(room.canManage)
        assertTrue(room.handoffEnabled)
        assertEquals(2, room.handoffMaxDepth)
        assertEquals(20, room.summaryEveryTurns)
        assertEquals("m", room.summaryModel)
        assertEquals(1, room.members.size)
        assertEquals("admin", room.members.single().name)
        assertTrue(room.members.single().online)
        assertEquals(1, room.agentList.size)
        assertEquals("Hermes", room.agentList.single().name)
        assertEquals("deepseek", room.agentList.single().model)
    }

    @Test
    fun `a global agent cannot be given a model, a remote one cannot be configured`() {
        val scoped = RoomAgent(id = "a", name = "n", agentMode = "scoped")
        val global = RoomAgent(id = "b", name = "n", agentMode = "global")
        val remote = RoomAgent(id = "c", name = "n", executorType = "remote")

        assertTrue(scoped.canConfigureModel)
        assertFalse("the server refuses a model on a global agent", global.canConfigureModel)
        assertFalse("a remote agent lives on another machine", remote.canConfigureModel)
        assertTrue(remote.isRemote)
        assertFalse(scoped.isRemote)
    }

    @Test
    fun `adding an agent sends only the fields that were filled in`() {
        enqueue("""{"agents":[]}""")

        api.addRoomAgent("r1", RoomAgentDraft(name = "Researcher", profile = "default", model = "m1"))

        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("Researcher", sent.getString("name"))
        assertEquals("default", sent.getString("profile"))
        assertEquals("m1", sent.getString("model"))
        // Empty strings must be absent, not sent as "" — the server validates types.
        assertFalse(sent.has("provider"))
        assertFalse(sent.has("reasoningEffort"))
        assertFalse(sent.has("agentPreset"))
    }

    @Test
    fun `the agent routes are the published ones`() {
        enqueue("""{"agents":[]}""")
        api.roomAgents("r1")
        assertEquals("/api/studio/group-chat/rooms/r1/agents", server.takeRequest().path)

        enqueue("""{}""")
        api.updateRoomAgent("r1", RoomAgent(id = "a1", name = "n", profile = "default"), RoomAgentDraft(name = "n2", profile = "default"))
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("/api/studio/group-chat/rooms/r1/agents/a1", update.path)

        enqueue("""{"agents":[]}""")
        api.deleteRoomAgent("r1", "a1")
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/studio/group-chat/rooms/r1/agents/a1", delete.path)
    }

    @Test
    fun `member removal goes through the members route`() {
        enqueue("""{"success":true}""")

        api.removeRoomMember("r1", "u2")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/studio/group-chat/rooms/r1/members/u2", request.path)
    }

    @Test
    fun `room config only sends the switch that changed`() {
        enqueue("""{}""")

        api.updateRoomConfig("r1", handoffEnabled = false)

        val sent = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(false, sent.getBoolean("agentHandoffEnabled"))
        assertFalse("untouched fields must not be overwritten", sent.has("summaryEveryTurns"))
        assertFalse(sent.has("name"))
    }

    @Test
    fun `presets parse and keep their model defaults`() {
        enqueue(
            """{"presets":[{"id":"p1","name":"Reviewer","agent":"hermes","profile":"default",
                 "provider":"custom","model":"m2","reasoningEffort":"high"}]}""",
        )

        val preset = api.agentPresets().single()

        assertEquals("p1", preset.id)
        assertEquals("Reviewer", preset.name)
        assertEquals("m2", preset.model)
        assertEquals("high", preset.reasoningEffort)
        assertEquals("/api/studio/group-chat/agent-presets", server.takeRequest().path)
    }

    @Test
    fun `the settings screen offers the three official surfaces`() {
        val screen = File("src/main/java/com/laozhudu/ekkostudio/RoomSettingsScreen.kt").readText()

        listOf("room_tab_agents", "room_tab_members", "room_tab_settings").forEach {
            assertTrue("$it missing from the tab row", screen.contains(it))
        }
        assertTrue(screen.contains("TabRow"))
        // Owner-only actions must be gated in the UI as well as by the server.
        assertTrue(screen.contains("canManage"))
        assertTrue(screen.contains("room_settings_readonly"))
    }

    @Test
    fun `clearing the context asks first`() {
        val screen = File("src/main/java/com/laozhudu/ekkostudio/RoomSettingsScreen.kt").readText()

        assertTrue(screen.contains("confirmClear"))
        assertTrue("the warning must say it cannot be undone", screen.contains("room_clear_context_note"))
    }

    @Test
    fun `the room screen can reach its settings and back returns to the room`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
        val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

        assertTrue(activity.contains("viewModel::openRoomSettings"))
        val back = KotlinSource.funBody(viewModel, "back")!!
        assertTrue("settings must pop to the conversation", back.contains("Screen.RoomSettings -> _state.update { it.copy(screen = Screen.Room) }"))
    }

    @Test
    fun `every new string exists in all three locales`() {
        val keys = listOf(
            "room_settings", "room_tab_agents", "room_agent_add", "room_agent_remote",
            "room_invite_code", "room_handoff", "room_clear_context", "room_context_cleared",
        )
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            keys.forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
