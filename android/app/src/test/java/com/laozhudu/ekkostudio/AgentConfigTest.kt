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
 * P6: coding agent update policy and the auxiliary model slots.
 *
 * Both were half-built: the policy route already returned `autoUpdate` and
 * `autoUpdateSupported` and the parser dropped them, and the auxiliary route had
 * no client at all. Shapes below were read from the live server.
 */
class AgentConfigTest {
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
    fun `an update policy keeps its auto-update fields`() {
        enqueue(
            """{"agents":{"qwen":{"autoUpdate":true,"checkedAt":"2026-10-21T12:23:04.126Z",
                 "currentVersion":"0.1.0","latestVersion":"0.25.3","status":"outdated",
                 "autoUpdateSupported":true}}}""",
        )

        val policy = api.codingAgentPolicies().single()

        assertTrue("the flag used to be dropped by the parser", policy.autoUpdate)
        assertTrue(policy.autoUpdateSupported)
        assertEquals("0.25.3", policy.latest)
        assertEquals("2026-10-21T12:23:04.126Z", policy.checkedAt)
    }

    @Test
    fun `the policy toggle is a single boolean on its own route`() {
        enqueue("""{"ok":true}""")

        api.setCodingAgentUpdatePolicy("qwen", autoUpdate = false)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/coding-agents/qwen/update-policy", request.path)
        assertEquals(false, JSONObject(request.body.readUtf8()).getBoolean("autoUpdate"))
    }

    @Test
    fun `an agent that is not installed reports no models`() {
        enqueue(
            """{"agents":[{"agentId":"codex","name":"Codex","status":"not_installed","models":[],"cached":false}]}""",
        )

        val models = api.codingAgentModels().single()

        assertEquals("not_installed", models.status)
        assertTrue(models.models.isEmpty())
    }

    @Test
    fun `auxiliary slots parse with their defaults`() {
        enqueue(
            """{"tasks":[{"key":"vision","label":"Vision","default_timeout":120,"default_download_timeout":30},
                 {"key":"compression","label":"Compression","default_timeout":120}],
               "auxiliary":{}}""",
        )

        val config = api.auxiliaryConfig("default")

        assertEquals(2, config.tasks.size)
        assertEquals(120, config.tasks.first().defaultTimeout)
        assertEquals(30, config.tasks.first().defaultDownloadTimeout)
        assertTrue("nothing assigned yet", config.assignments.isEmpty())
        assertEquals("/api/hermes/config/auxiliary-models?profile=default", server.takeRequest().path)
    }

    @Test
    fun `an unassigned slot reads as inherited, not as a blank model`() {
        val config = AuxiliaryConfig(
            tasks = listOf(AuxiliaryTask("vision", "Vision", 120, 0)),
            assignments = emptyMap(),
        )

        val assignment = config.assignmentFor("vision")

        assertTrue(assignment.isInherited)
        assertFalse(config.assignmentFor("vision").model.isNotBlank())
    }

    @Test
    fun `saving sends only the fields the server's normaliser accepts`() {
        enqueue("""{"success":true}""")

        api.saveAuxiliaryConfig(
            "default",
            mapOf("vision" to AuxiliaryAssignment(provider = "openrouter", model = "m", timeout = 60)),
        )

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        val auxiliary = JSONObject(request.body.readUtf8()).getJSONObject("auxiliary")
        val vision = auxiliary.getJSONObject("vision")
        assertEquals("openrouter", vision.getString("provider"))
        assertEquals("m", vision.getString("model"))
        assertEquals(60, vision.getInt("timeout"))
        // Never sent: api_key is on the wire but a key typed on a phone is a key
        // in a screenshot, and extra_body is not something a phone should edit.
        assertFalse(vision.has("api_key"))
        assertFalse(vision.has("extra_body"))
        assertFalse(vision.has("download_timeout"))
    }

    @Test
    fun `an inherited slot is omitted from the payload instead of being sent empty`() {
        enqueue("""{"success":true}""")

        api.saveAuxiliaryConfig("default", mapOf("vision" to AuxiliaryAssignment(provider = " ", model = "")))

        val auxiliary = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("auxiliary")
        assertFalse("an empty entry would overwrite the slot", auxiliary.has("vision"))
    }

    @Test
    fun `delegation reads and writes provider model and effort`() {
        enqueue("""{"delegation":{"provider":"openrouter","model":"m2","reasoning_effort":"high"}}""")
        val delegation = api.delegationConfig("default")
        assertEquals("openrouter", delegation.provider)
        assertEquals("high", delegation.reasoningEffort)
        // Consume the GET, or the next takeRequest() returns it instead of the PUT.
        assertEquals("/api/hermes/config/delegation-model?profile=default", server.takeRequest().path)

        enqueue("""{"success":true}""")
        api.saveDelegationConfig("default", delegation)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/api/hermes/config/delegation-model", put.path.orEmpty().substringBefore('?'))
        val body = JSONObject(put.body.readUtf8()).getJSONObject("delegation")
        assertEquals("m2", body.getString("model"))
        assertEquals("high", body.getString("reasoning_effort"))
    }

    @Test
    fun `the auxiliary screen is reachable and edits a local draft first`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
        val screen = File("src/main/java/com/laozhudu/ekkostudio/AuxiliaryModelsScreen.kt").readText()
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

        assertTrue(activity.contains("Screen.AuxiliaryModels -> AuxiliaryModelsScreen(state, viewModel)"))
        assertTrue(activity.contains("viewModel.openAuxiliaryModels()"))
        assertTrue(KotlinSource.hasFun(vm, "saveAuxiliaryConfig"))
        // The PUT replaces the whole map, so a stray recomposition must not save.
        assertTrue("edits stay in a draft until Save", screen.contains("var draft by remember(config)"))
        assertTrue(screen.contains("enabled = dirty"))
    }

    @Test
    fun `the coding agent card exposes update state, not just install state`() {
        val screen = File("src/main/java/com/laozhudu/ekkostudio/AgentRuntimeScreen.kt").readText()

        assertTrue(screen.contains("coding_agent_auto_update"))
        assertTrue(screen.contains("coding_agent_check_update"))
        assertTrue(screen.contains("coding_agent_install"))
        assertTrue(screen.contains("coding_agent_models"))
        // Only the coding family has these rows.
        assertTrue(screen.contains("if (runtime.family == \"coding\")"))
    }

    @Test
    fun `skills already had edit toggle pin and delete, so nothing is invented`() {
        val api = File("src/main/java/com/laozhudu/ekkostudio/HermesApi.kt").readText()
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

        listOf("saveSkill", "deleteSkill").forEach { assertTrue(KotlinSource.hasFun(api, it)) }
        listOf("toggleSkill", "pinSkill").forEach { assertTrue(KotlinSource.hasFun(vm, it)) }
    }

    @Test
    fun `every new string exists in all three locales`() {
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            listOf(
                "auxiliary_title", "auxiliary_note", "auxiliary_inherit",
                "coding_agent_auto_update", "coding_agent_install",
            ).forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
