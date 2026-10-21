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
 * Contracts for the three usage/model surfaces added in 0.16.0.
 *
 * These probe live shapes: an empty pricing table, an empty key list and an
 * empty job queue are all normal answers, so "empty" must never be confused
 * with "the request failed".
 */
class UsageAndRuntimeContractTest {
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
    fun `an empty pricing table is empty, not an error`() {
        enqueue("""{"rates":[]}""")

        assertTrue(api.usagePricing().isEmpty())
        assertEquals("/api/studio/usage/pricing", server.takeRequest().path)
    }

    @Test
    fun `pricing keeps optional cache rates null instead of zero`() {
        enqueue("""{"rates":[{"provider":"openrouter","model":"x/y","input":3,"output":15}]}""")

        val rate = api.usagePricing().single()

        assertEquals("openrouter", rate.provider)
        assertEquals("x/y", rate.model)
        assertEquals(3.0, rate.input, 0.0001)
        assertNull(rate.cacheRead)
        assertNull(rate.cacheWrite)
    }

    @Test
    fun `saving pricing replaces the whole table and nulls empty cache rates`() {
        enqueue("""{"rates":[]}""")

        api.saveUsagePricing(
            listOf(
                PricingRate("openrouter", "a/b", 3.0, 15.0, cacheRead = 0.3, cacheWrite = null),
            ),
        )

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/studio/usage/pricing", request.path)
        val sent = JSONObject(request.body.readUtf8())
        val rate = sent.getJSONArray("rates").getJSONObject(0)
        assertEquals("openrouter", rate.getString("provider"))
        assertEquals(0.3, rate.getDouble("cacheRead"), 0.0001)
        assertTrue("cacheWrite must be JSON null, not 0", rate.isNull("cacheWrite"))
    }

    @Test
    fun `model catalog only offers models the key scope will accept`() {
        enqueue(
            """{"data":[
                {"id":"openrouter/a/b","owned_by":"openrouter","responses_supported":true},
                {"id":"openrouter/c/d","owned_by":"openrouter","responses_supported":false}
            ]}""",
        )

        val catalog = api.modelApiCatalog().filter { it.responsesSupported }

        assertEquals(1, catalog.size)
        assertEquals("openrouter/a/b", catalog.single().id)
        assertEquals("/api/ekko-studio/models/v1/models", server.takeRequest().path)
    }

    @Test
    fun `creating a key returns the secret exactly once and the scope is sent`() {
        enqueue(
            """{"key":{"id":"k1","name":"phone","preview":"sk-ekko-…abcd","models":["openrouter/a/b"],
                 "budgetUsd":5,"spentUsd":0,"unpricedRequests":0,"createdAt":1700000000000,"lastUsedAt":null},
                "apiKey":"sk-ekko-secretvalue"}""",
            code = 201,
        )

        val created = api.createModelApiKey("phone", listOf("openrouter/a/b"), 5.0)

        assertEquals("sk-ekko-secretvalue", created.secret)
        assertEquals("k1", created.key.id)
        assertNull(created.key.lastUsedAt)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/studio/models/api-keys", request.path)
        val sent = JSONObject(request.body.readUtf8())
        assertEquals("phone", sent.getString("name"))
        assertEquals("openrouter/a/b", sent.getJSONArray("models").getString(0))
        assertEquals(5.0, sent.getDouble("budgetUsd"), 0.0001)
    }

    @Test
    fun `an unlimited key sends a JSON null budget`() {
        enqueue("""{"key":{"id":"k2","name":"all","preview":"p","models":["m"],"budgetUsd":null,
                   "spentUsd":0,"unpricedRequests":0,"createdAt":1,"lastUsedAt":null},"apiKey":"s"}""", code = 201)

        val created = api.createModelApiKey("all", listOf("m"), null)

        assertNull(created.key.budgetUsd)
        assertTrue(JSONObject(server.takeRequest().body.readUtf8()).isNull("budgetUsd"))
        assertEquals("s", created.secret)
    }

    @Test
    fun `revoking a key deletes by id`() {
        enqueue("""{"success":true}""")

        api.deleteModelApiKey("k1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/studio/models/api-keys/k1", request.path)
    }

    @Test
    fun `runtime versions expose the CLI installs and the source`() {
        enqueue(
            """{"platform":"linux-x64","hermes":{"activeVersion":"","agentVersion":"0.21.7","source":"user-cli",
                "installed":[],"remoteVersions":["0.22.2"],
                "cliInstallations":[{"path":"/usr/local/bin/hermes","version":"0.21.7"}]},
                "webui":{"currentVersion":"0.7.38","activeVersion":"0.7.38","installed":[],"remoteVersions":[]}}""",
        )

        val versions = api.runtimeVersions()

        assertEquals("user-cli", versions.source)
        assertEquals("0.21.7", versions.agentVersion)
        assertEquals("0.22.2", versions.remoteRuntime.single())
        assertTrue(versions.remoteWebUi.isEmpty())
        assertEquals("0.21.7", versions.cliInstallations.single().first)
    }

    @Test
    fun `a job without percent is still live and keeps its bytes`() {
        enqueue(
            """{"jobs":[{"id":"j1","kind":"runtime","version":"0.22.2","status":"running","stage":"download",
                 "receivedBytes":1048576,"totalBytes":10485760,"error":"","createdAt":"2026-10-21T00:00:00Z"}]}""",
        )

        val job = api.runtimeJobs().single()

        assertTrue(job.isLive)
        assertNull(job.percent)
        assertEquals(1_048_576L, job.receivedBytes)
    }

    @Test
    fun `a finished job is no longer live`() {
        enqueue(
            """{"jobs":[{"id":"j2","kind":"webui","version":"0.7.39","status":"completed","stage":"completed","percent":100}]}""",
        )

        val job = api.runtimeJobs().single()

        assertFalse(job.isLive)
        assertEquals(100.0, job.percent!!, 0.0001)
    }

    @Test
    fun `deleting a web UI version hits the webui route, not the runtime one`() {
        enqueue("""{"success":true}""")

        api.deleteRuntimeVersion("0.7.38", webUi = true)

        assertEquals("/api/hermes/runtime-versions/webui/0.7.38", server.takeRequest().path)
    }
}
