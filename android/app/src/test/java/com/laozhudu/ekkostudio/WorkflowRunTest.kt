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
 * P5: a workflow run's detail and its read-only graph.
 *
 * The field names are not guesses — a probe workflow was created on the live
 * server, run once, read, and deleted. The run record uses `started_at` /
 * `finished_at` (not `created_at` / `completed_at`) and carries `node_sessions`,
 * which was the missing piece: the list view could say a run failed but not
 * which node failed.
 */
class WorkflowRunTest {
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

    private val runJson = """
        {"runs":[{"id":"run-1","workflow_id":"wf-1","status":"completed",
          "started_at":1792597642882,"finished_at":1792597653739,
          "created_at":1792597642800,"trigger_source":"manual",
          "node_sessions":[
            {"id":"ns-1","node_id":"n1","status":"completed","session_id":"s-1",
             "started_at":1792597642894,"finished_at":1792597653739,"error":null},
            {"id":"ns-2","node_id":"n2","status":"failed","session_id":"s-2",
             "started_at":1792597642900,"finished_at":1792597643000,"error":"boom"}]}]}
    """.trimIndent()

    @Test
    fun `a run keeps its node sessions and its real timestamps`() {
        enqueue(runJson)

        val run = api.workflowRuns("wf-1").single()

        assertEquals(2, run.nodeSessions.size)
        assertEquals("n2", run.nodeSessions[1].nodeId)
        assertEquals("boom", run.nodeSessions[1].error)
        assertEquals(1792597642882L, run.startedAt)
        assertEquals("manual", run.triggerSource)
        // 10.857 s between started_at and finished_at.
        assertEquals(10_857L, run.durationMillis)
    }

    @Test
    fun `the detail route reads the same shape it lists`() {
        enqueue("""{"run":{"id":"run-1","workflow_id":"wf-1","status":"running","started_at":1000,
                     "node_sessions":[{"id":"ns-1","node_id":"n1","status":"blocked"}]}}""")

        val run = api.workflowRun("wf-1", "run-1")

        assertEquals("running", run.status)
        assertEquals("n1", run.pendingNodeId)
        assertTrue("a running run is stoppable", run.running)
        assertEquals("/api/studio/workflows/wf-1/runs/run-1", server.takeRequest().path)
    }

    @Test
    fun `a run without timestamps reports no duration, not zero`() {
        enqueue("""{"runs":[{"id":"r","workflow_id":"w","status":"queued","node_sessions":[]}]}""")

        val run = api.workflowRuns("w").single()

        assertNull(run.durationMillis)
        assertTrue(run.running)
    }

    @Test
    fun `durations are rendered at a useful scale`() {
        assertEquals("850ms", formatDuration(850))
        assertEquals("10.9s", formatDuration(10_857))
        assertEquals("2m5s", formatDuration(125_000))
    }

    @Test
    fun `nodes parse their canvas positions and fall back to a grid`() {
        val positioned = parseGraphNodes(
            """[{"id":"n1","position":{"x":40,"y":-20}},{"id":"n2","position":{"x":200,"y":60}}]""",
        )
        val layout = layoutWorkflowNodes(positioned)
        // Negative coordinates are normalised so the graph starts at the origin.
        assertEquals(0f, layout["n1"]!!.first)
        assertEquals(0f, layout["n1"]!!.second)
        assertEquals(160f, layout["n2"]!!.first)

        val bare = parseGraphNodes("""[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]""")
        val grid = layoutWorkflowNodes(bare)
        assertEquals(4, grid.size)
        assertTrue("a workflow without positions still lays out", grid.values.toSet().size == 4)
    }

    @Test
    fun `edges are read from either naming convention`() {
        val edges = parseGraphEdges("""[{"source":"a","target":"b"},{"from":"b","to":"c"},{"source":"x"}]""")

        assertEquals(listOf("a" to "b", "b" to "c"), edges.map { it.from to it.to })
    }

    @Test
    fun `the run screen is reachable and stoppable`() {
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
        val screen = File("src/main/java/com/laozhudu/ekkostudio/WorkflowRunScreen.kt").readText()

        assertTrue(KotlinSource.hasFun(vm, "openWorkflowRun"))
        assertTrue(KotlinSource.hasFun(vm, "reloadWorkflowRun"))
        assertTrue(activity.contains("Screen.WorkflowRun -> WorkflowRunScreen(state, viewModel)"))
        assertTrue(screen.contains("workflow_run_stop"))
        // Read-only by design: dragging nodes is a desktop job.
        assertTrue(screen.contains("workflow_graph_hint"))
        assertTrue("no dragging on a phone canvas", !screen.contains("detectDragGestures"))
    }

    @Test
    fun `the workflow list opens a run when its row is tapped`() {
        val ops = File("src/main/java/com/laozhudu/ekkostudio/StudioOperationsScreens.kt").readText()

        assertTrue(ops.contains("onOpenRun = viewModel::openWorkflowRun"))
        assertTrue(ops.contains("clickable { onOpenRun(run) }"))
    }

    @Test
    fun `every new string exists in all three locales`() {
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            listOf("workflow_run_stop", "workflow_run_nodes", "workflow_graph_hint").forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
