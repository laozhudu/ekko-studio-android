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

/**
 * Workspace folder shortcuts.
 *
 * Contract verified on 0.7.38: `GET /api/studio/workspace/directories` answers
 * `{"directories":[{"path":…,"favorite":…}]}`; `POST` takes `{path}` and `PATCH`
 * takes `{path, favorite}`. The official Android build uses the same three routes,
 * which is why they are modelled here rather than with a local preference.
 */
class WorkspaceDirectoryContractTest {

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
    fun directoriesAreParsedWithTheirFavoriteFlag() {
        val favourite = JSONObject().put("path", "workspace/ekko-studio-android").put("favorite", true)
        val plain = JSONObject().put("path", "workspace/质量分析")
        server.enqueue(
            MockResponse().setBody(
                JSONObject().put("directories", org.json.JSONArray().put(favourite).put(plain)).toString(),
            ),
        )

        val directories = api.workspaceDirectories()

        assertEquals(2, directories.size)
        assertEquals("workspace/ekko-studio-android", directories[0].path)
        assertTrue(directories[0].favorite)
        // A missing flag means "not favourited", not "malformed".
        assertFalse(directories[1].favorite)
        assertEquals("/api/studio/workspace/directories", server.takeRequest().path)
    }

    @Test
    fun addingADirectoryPostsOnlyThePath() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.addWorkspaceDirectory("workspace/备份")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/studio/workspace/directories", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("workspace/备份", body.getString("path"))
        assertFalse("favorite must not be invented on create", body.has("favorite"))
    }

    @Test
    fun favouritingPatchesPathAndFlag() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        api.setWorkspaceDirectoryFavorite("workspace/备份", true)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("workspace/备份", body.getString("path"))
        assertTrue(body.getBoolean("favorite"))
    }
}
