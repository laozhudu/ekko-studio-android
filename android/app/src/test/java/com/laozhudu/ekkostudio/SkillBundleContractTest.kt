package com.laozhudu.ekkostudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Skill bundles: a named group of skills exposed as its own slash command.
 *
 * Contract verified against 0.7.38: `POST /api/hermes/bundles` takes
 * `{name, description, skills[]}` and answers 201 `{bundle:…}`;
 * `DELETE /api/hermes/bundles/{commandName}` removes it.
 */
class SkillBundleContractTest {

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
    fun bundlesAreParsedFromTheListRoute() {
        val first = JSONObject()
            .put("name", "release")
            .put("description", "Ship checklist")
            .put("skills", org.json.JSONArray().put("git").put("test"))
        val second = JSONObject()
            .put("name", "review")
            .put("skills", org.json.JSONArray().put(JSONObject().put("name", "lint")))
        server.enqueue(
            MockResponse().setBody(
                JSONObject().put("bundles", org.json.JSONArray().put(first).put(second)).toString(),
            ),
        )

        val bundles = api.bundles()

        assertEquals(listOf("release", "review"), bundles.map { it.name })
        assertEquals("Ship checklist", bundles[0].description)
        assertEquals(listOf("git", "test"), bundles[0].skills)
        // The command falls back to the name when the server omits it.
        assertEquals("release", bundles[0].command)
        // Skills may arrive as objects rather than strings.
        assertEquals(listOf("lint"), bundles[1].skills)
        assertEquals("/api/hermes/bundles", server.takeRequest().path)
    }

    @Test
    fun createSendsTheDocumentedBody() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"bundle":{"name":"release"}}"""))

        api.createBundle("release", "Ship checklist", listOf("git", "test"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/hermes/bundles", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("release", body.getString("name"))
        assertEquals("Ship checklist", body.getString("description"))
        assertEquals(2, body.getJSONArray("skills").length())
        assertEquals("git", body.getJSONArray("skills").getString(0))
    }

    @Test
    fun deleteAddressesTheCommandName() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.deleteBundle("release")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/hermes/bundles/release", request.path)
    }
}
