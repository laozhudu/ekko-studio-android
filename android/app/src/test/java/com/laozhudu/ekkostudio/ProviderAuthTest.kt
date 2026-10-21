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
import java.io.File

/**
 * P8: provider sign-in.
 *
 * The server has two distinct flows and the app has to handle both rather than
 * pretend there is one:
 *
 *  - device code (`nous`, `copilot`): start returns `user_code` +
 *    `verification_url`; a worker polls the provider and `poll/{sessionId}` reports
 *    `{status, error}`.
 *  - authorization URL (`anthropic`, `codex`, `xai`, `minimax`): start returns
 *    `authorization_url`; only anthropic has a `submit` route, and only anthropic
 *    has **no** poll route — its completion shows up in `status` alone.
 */
class ProviderAuthTest {
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
    fun `the provider list matches the two flows the server actually has`() {
        assertEquals(6, OAUTH_PROVIDERS.size)

        val device = OAUTH_PROVIDERS.filter { it.flow == OAuthFlow.DeviceCode }.map { it.id }
        val url = OAUTH_PROVIDERS.filter { it.flow == OAuthFlow.AuthorizationUrl }.map { it.id }

        assertEquals(listOf("nous", "copilot"), device)
        assertEquals(listOf("anthropic", "codex", "xai", "minimax"), url)
        // Only anthropic asks for a pasted code, and only copilot has an on/off switch.
        assertEquals(listOf("anthropic"), OAUTH_PROVIDERS.filter { it.supportsSubmit }.map { it.id })
        assertEquals(listOf("copilot"), OAUTH_PROVIDERS.filter { it.toggles }.map { it.id })
    }

    @Test
    fun `a device-code start carries a code and a verification url`() {
        enqueue(
            """{"session_id":"s1","user_code":"ABCD-1234",
                "verification_url":"https://portal.example/device","expires_in":600}""",
        )

        val start = api.startProviderAuth("nous", "default")

        assertEquals("s1", start.sessionId)
        assertEquals("ABCD-1234", start.userCode)
        assertEquals("https://portal.example/device", start.verificationUrl)
        assertTrue(start.isDeviceCode)
        assertEquals("/api/hermes/auth/nous/start", server.takeRequest().path.substringBefore('?'))
    }

    @Test
    fun `an authorization-url start carries a url and no code`() {
        enqueue("""{"session_id":"s2","authorization_url":"https://auth.example/authorize?x=1","expires_in":900}""")

        val start = api.startProviderAuth("codex", "default")

        assertEquals("https://auth.example/authorize?x=1", start.authorizationUrl)
        assertFalse("there is no code to type in this flow", start.isDeviceCode)
        assertNull(start.userCode)
    }

    @Test
    fun `a status read understands both shapes the server returns`() {
        // Most providers answer `authenticated`; copilot answers has_token/enabled.
        enqueue("""{"authenticated":false,"relogin_required":true}""")
        val nous = api.providerAuthStatus("nous", "default")
        assertFalse(nous.authenticated)
        assertTrue(nous.reloginRequired)

        enqueue("""{"has_token":true,"source":"gho","enabled":false}""")
        val copilot = api.providerAuthStatus("copilot", "default")
        assertTrue("has_token means signed in", copilot.authenticated)
        assertFalse(copilot.enabled)
        assertEquals("gho", copilot.source)
    }

    @Test
    fun `a poll reports progress and its terminal states`() {
        enqueue("""{"status":"pending","error":null}""")
        val pending = api.pollProviderAuth("nous", "s1", "default")
        assertEquals("pending", pending.status)
        assertFalse(pending.finished)
        assertFalse(pending.failed)

        enqueue("""{"status":"completed"}""")
        assertTrue(api.pollProviderAuth("nous", "s1", "default").finished)

        enqueue("""{"status":"expired"}""")
        val expired = api.pollProviderAuth("nous", "s1", "default")
        assertTrue(expired.failed)
        assertFalse(expired.finished)
    }

    @Test
    fun `the pasted code goes to the submit route`() {
        enqueue("""{"ok":true}""")

        api.submitProviderCode("anthropic", "s3", "  code-123  ", "default")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/hermes/auth/anthropic/submit/s3", request.path.substringBefore('?'))
        assertEquals("code-123", JSONObject(request.body.readUtf8()).getString("code"))
    }

    @Test
    fun `copilot enable and disable are separate routes`() {
        enqueue("""{"ok":true}""")
        api.setCopilotEnabled(false, "default")
        assertEquals("/api/hermes/auth/copilot/disable", server.takeRequest().path.substringBefore('?'))

        enqueue("""{"ok":true}""")
        api.setCopilotEnabled(true, "default")
        assertEquals("/api/hermes/auth/copilot/enable", server.takeRequest().path.substringBefore('?'))
    }

    @Test
    fun `the poller skips the poll route for anthropic, which has none`() {
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
        val poll = KotlinSource.funBody(vm, "pollProviderAuth")!!

        assertTrue("anthropic is polled through status only", poll.contains("if (!provider.supportsSubmit)"))
        assertTrue("and status is the fallback for everyone", poll.contains("providerAuthStatus"))
        assertTrue("a finished flow stops the loop", poll.contains("if (!flow.polling || flow.finished || flow.failed) return@launch"))
    }

    @Test
    fun `every provider is read independently`() {
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
        val load = KotlinSource.funBody(vm, "loadProviders")!!
        // One provider whose status call fails must not blank the whole list.
        assertTrue(load.contains("runCatching { api.providerAuthStatus(provider.id, profile) }.getOrNull()"))
        assertTrue(load.contains("mapNotNull"))
    }

    @Test
    fun `the screen is reachable and offers both halves of each flow`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
        val screen = File("src/main/java/com/laozhudu/ekkostudio/ProvidersScreen.kt").readText()

        assertTrue(activity.contains("Screen.Providers -> ProvidersScreen(state, viewModel)"))
        assertTrue(activity.contains("viewModel.openProviders()"))
        // A device code has to be copyable; a URL has to be openable.
        assertTrue(screen.contains("clipboard.setText(AnnotatedString(userCode))"))
        assertTrue(screen.contains("openExternalLink(context, target)"))
        assertTrue("the pasted-code field is only for providers that need it", screen.contains("if (active.supportsSubmit)"))
    }

    @Test
    fun `every new string exists in all three locales`() {
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            listOf(
                "providers_title", "provider_device_code_note", "provider_authorize_note",
                "provider_paste_code", "provider_waiting",
            ).forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
