package xyz.rflg.hstudiodirect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the official `pages/devices` page to the APK it was transcribed from.
 *
 * Two kinds of claim are checked, and they are different claims:
 *
 * 1. **Display helpers** — the ports of the official render helpers
 *    (`it()`, `Xe()`, `nt()`, `rt()`, `Aa()`) are called directly and compared
 *    against the behaviour read out of the APK's `app-service.js`.
 * 2. **Layout constants** — the Compose source is read as text and required to
 *    still carry the numbers the official stylesheet defines. There is no
 *    Compose runtime in this repo's tests, so this is a source assertion, not a
 *    rendering assertion; it catches drift, not misplacement.
 *
 * The official numbers are re-read from `docs/reference/apk-1.0.3/css/pages-devices.css`
 * rather than duplicated here, so a change to the reference fails the test too.
 *
 * Like every test in this repo it has never been executed on the authoring host —
 * see docs/parity/baseline-tests.md for the ARM64 aapt2 blocker.
 */
class DevicesParityTest {

    private val screen = File("src/main/java/xyz/rflg/hstudiodirect/DevicesScreen.kt").readText()
    private val instances = File("src/main/java/xyz/rflg/hstudiodirect/Instances.kt").readText()
    private val viewModel = File("src/main/java/xyz/rflg/hstudiodirect/AppViewModel.kt").readText()
    private val store = File("src/main/java/xyz/rflg/hstudiodirect/Store.kt").readText()
    private val navigation = File("src/main/java/xyz/rflg/hstudiodirect/MainActivity.kt").readText()

    /**
     * The stylesheet with uni-app's `[data-v-<hash>]` scope markers stripped, so
     * the needles below read like the rules do in the source. `scripts/apk/css-rules.py`
     * does the same thing for the same reason.
     */
    private val css = File("../../docs/reference/apk-1.0.3/css/pages-devices.css")
        .readText()
        .replace(Regex("""\[data-v-[0-9a-f]+\]"""), "")

    // ── ground truth is present and still says what was implemented ───────

    @Test
    fun officialStylesheetStillDefinesTheGridBeingImplemented() {
        assertTrue("devices stylesheet missing", css.isNotEmpty())
        listOf(
            ".device-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}",
            ".device-card{display:flex;min-width:0;overflow:hidden;border:1px solid var(--ink-border-light)",
            "min-height:170px;padding:17px;background:var(--ink-bg-card);border-radius:17px",
            "width:31px;height:31px",
            "width:31px;height:22px;border:1.5px solid var(--ink-text-primary);border-radius:5px",
            "width:5px;height:5px;background:currentColor;border-radius:50%",
            ".device-status--online{color:#397454;background:rgba(57,116,84,.1)}",
            ".device-version-row .device-source-tag--local{color:#397454;background:rgba(57,116,84,.1)}",
            ".device-version-row .device-source-tag--cloud{color:#7657a6;background:rgba(118,87,166,.1)}",
            ".device-version-row .device-endpoint-tag--web{color:#3f648f;background:rgba(63,100,143,.1)}",
            "min-height:68px;padding:11px 12px;gap:11px",
            "height:44px;padding:0 13px",
            "max-width:440px;padding:20px",
            "width:260px;padding:8px",
            "top:43px;right:0",
            "@media (max-width: 520px)",
            "@media (orientation: landscape) and (max-height: 599px)",
            "@media (orientation: landscape) and (min-width: 900px) and (min-height: 600px)",
        ).forEach { needle ->
            assertTrue("official devices CSS no longer contains: $needle", css.contains(needle))
        }
    }

    @Test
    fun everyOfficialNumberIsCarriedIntoTheComposeSource() {
        // Each pair is (official CSS value, the Compose literal that encodes it).
        listOf(
            "min-height:170px" to "170.dp",
            "border-radius:17px" to "17.dp",
            "padding:17px" to "cardPadding = 17.dp",
            "gap:12px" to "gridGap = 12.dp",
            "min-height:156px" to "156.dp",
            "width:260px" to "260.dp",
            "top:43px" to "43.dp",
            "width:31px;height:31px" to "size(31.dp)",
            "border-radius:5px" to "RoundedCornerShape(5.dp)",
            "width:2px;height:2px" to "size(2.dp)",
            "width:11px;height:6px" to "size(width = 11.dp, height = 6.dp)",
            "min-height:68px" to "defaultMinSize(minHeight = 68.dp)",
            "max-width:440px" to "440.dp",
            "border-radius:20px" to "RoundedCornerShape(20.dp)",
            "border-radius:13px" to "RoundedCornerShape(13.dp)",
            "border-radius:14px" to "RoundedCornerShape(14.dp)",
            "border-radius:12px" to "RoundedCornerShape(12.dp)",
            "border-radius:11px" to "RoundedCornerShape(11.dp)",
            "border-radius:10px" to "RoundedCornerShape(10.dp)",
            "border-radius:8px" to "RoundedCornerShape(8.dp)",
            "border-radius:7px" to "RoundedCornerShape(7.dp)",
            "border-radius:6px" to "RoundedCornerShape(6.dp)",
            "height:38px" to "38.dp",
            "max-width:680px" to "maxWidth = 680.dp",
            "max-width:1240px" to "maxWidth = 1240.dp",
            "font-size:22px" to "titleSize = 22.sp",
            "font-size:19px" to "titleSize = 19.sp",
            "min-width: 900px" to "screenWidthDp >= 900",
            "max-height: 599px" to "screenHeightDp < 600",
            "max-width: 520px" to "screenWidthDp <= 520",
        ).forEach { (official, compose) ->
            assertTrue("official CSS lost $official", css.contains(official))
            assertTrue("Compose source no longer mirrors $official (expected $compose)", screen.contains(compose))
        }
    }

    /** The badge/status colours the official stylesheet hard-codes, both themes. */
    @Test
    fun badgePaletteMatchesTheOfficialLightAndDarkValues() {
        listOf(
            "0xFF397454" to "device-source-tag--local light / device-status--online",
            "0xFF8FC9A5" to "device-source-tag--local dark",
            "0xFF7657A6" to "device-source-tag--cloud light",
            "0xFFBEA7E3" to "device-source-tag--cloud dark",
            "0xFF3F648F" to "device-endpoint-tag--web light",
            "0xFF99BDE6" to "device-endpoint-tag--web dark",
        ).forEach { (compose, what) ->
            assertTrue("$what is missing from the device palette", screen.contains(compose))
        }
        listOf(
            "#397454", "#8fc9a5", "#7657a6", "#bea7e3", "#3f648f", "#99bde6",
        ).forEach { hex ->
            assertTrue("official CSS no longer defines $hex", css.lowercase().contains(hex))
        }
    }

    // ── ported render helpers ─────────────────────────────────────────────

    @Test
    fun deviceNameIsClampedTheWayTheOfficialCardClampsIt() {
        assertEquals("short", deviceDisplayName("short"))
        val forty = "a".repeat(40)
        assertEquals(forty, deviceDisplayName(forty))
        assertEquals(forty + "...", deviceDisplayName("a".repeat(41)))
        assertEquals("studio", deviceDisplayName("  studio  "))
    }

    @Test
    fun systemLabelMapsTheOfficialOsFamilies() {
        assertEquals("Apple", deviceSystemLabel("darwin"))
        assertEquals("Apple", deviceSystemLabel("macOS 14"))
        assertEquals("Windows", deviceSystemLabel("win32"))
        assertEquals("Linux", deviceSystemLabel("linux"))
        // Unknown or absent: the page falls back, it does not invent an OS.
        assertEquals("", deviceSystemLabel("freebsd"))
        assertEquals("", deviceSystemLabel(""))
    }

    @Test
    fun endpointKindIsOnlyDesktopOrWeb() {
        assertEquals("desktop", deviceEndpointKind("Desktop"))
        assertEquals("web", deviceEndpointKind(" web "))
        assertEquals("", deviceEndpointKind("terminal"))
        assertEquals("", deviceEndpointKind(""))
    }

    @Test
    fun lastSeenBucketsFollowTheOfficialBranches() {
        val now = 1_000_000L
        assertEquals(DeviceAge.Online, deviceAge(online = true, lastSeenAt = 0, nowSeconds = now))
        assertEquals(DeviceAge.Never, deviceAge(online = false, lastSeenAt = 0, nowSeconds = now))
        assertEquals(DeviceAge.JustNow, deviceAge(false, now - 59, now))
        assertEquals(DeviceAge.Minutes, deviceAge(false, now - 60, now))
        assertEquals(DeviceAge.Minutes, deviceAge(false, now - 3599, now))
        assertEquals(DeviceAge.Hours, deviceAge(false, now - 3600, now))
        assertEquals(DeviceAge.Hours, deviceAge(false, now - 86_399, now))
        assertEquals(DeviceAge.Days, deviceAge(false, now - 86_400, now))
        // A clock that moved backwards must not produce a negative count.
        assertEquals(DeviceAge.JustNow, deviceAge(false, now + 500, now))

        assertEquals(5L, deviceAgeCount(DeviceAge.Minutes, now - 300, now))
        assertEquals(2L, deviceAgeCount(DeviceAge.Hours, now - 7200, now))
        assertEquals(3L, deviceAgeCount(DeviceAge.Days, now - 3 * 86_400, now))
        assertEquals(0L, deviceAgeCount(DeviceAge.Online, 0, now))
    }

    // ── ported connection normaliser (official `Aa(connection)`) ──────────

    @Test
    fun connectionNormaliserKeepsPlainHttpForLanDevices() {
        // The official extractor preserves the scheme it was given; a LAN Studio
        // is http, and upgrading it to https (as the sign-in field does) would
        // fail to connect.
        assertEquals("http://192.168.1.5:8080", normalizeDeviceConnection("http://192.168.1.5:8080"))
        assertEquals("https://studio.example.com", normalizeDeviceConnection("https://studio.example.com"))
        assertEquals("http://192.168.1.5:8080", normalizeDeviceConnection("http://192.168.1.5:8080/"))
        // Path and query are dropped: the device address is the origin.
        assertEquals("http://192.168.1.5:8080", normalizeDeviceConnection("http://192.168.1.5:8080/chat?x=1"))
    }

    @Test
    fun connectionNormaliserUnwrapsAPastedPairingPayload() {
        // The official extractor looks for `connectionUrl=`/`url=` behind a
        // `?`, `&` or `#` separator, i.e. it unwraps querystring-shaped
        // payloads — which is what the Studio QR code carries.
        assertEquals(
            "http://10.0.0.7:8642",
            normalizeDeviceConnection("hermes://pair?connectionUrl=http%3A%2F%2F10.0.0.7%3A8642&x=1"),
        )
        assertEquals("https://a.example.com", normalizeDeviceConnection("junk url=https://a.example.com junk"))
        // A raw JSON body is NOT unwrapped, matching the official extractor: its
        // separator class has no `"`. Verified against the compiled `Aa()` — see
        // docs/parity/devices.md.
        assertNull(normalizeDeviceConnection("""{"connectionUrl":"http%3A%2F%2F10.0.0.7%3A8642"}"""))
    }

    @Test
    fun connectionNormaliserAcceptsABareHostAndRejectsNonsense() {
        // Documented addition: the official regex requires a scheme, but a
        // hand-typed LAN address is the primary path this app exists for.
        assertEquals("http://192.168.1.5:8080", normalizeDeviceConnection("192.168.1.5:8080"))
        assertEquals("http://studio.local", normalizeDeviceConnection("studio.local"))
        assertEquals("https://studio.example.com", normalizeDeviceConnection("studio.example.com"))

        assertNull(normalizeDeviceConnection(""))
        assertNull(normalizeDeviceConnection("   "))
        assertNull(normalizeDeviceConnection("http://host:99999"))
        assertNull(normalizeDeviceConnection("not a host"))
    }

    // ── the page is wired in, and the cloud stays unreachable ─────────────

    @Test
    fun devicesScreenIsTheNavigationTargetForTheOfficialPage() {
        assertTrue("MainActivity must dispatch Screen.Devices", navigation.contains("Screen.Devices -> DevicesScreen(state, viewModel)"))
        assertFalse("the old instance list must be gone", navigation.contains("InstancesScreen"))
        assertTrue("the chats shortcut must open the devices page", navigation.contains("viewModel.openDevices()"))
        assertTrue("back must be handled on the page", navigation.contains("Screen.Devices,"))
        assertTrue("back from devices returns to the chats list", viewModel.contains("Screen.Devices -> Screen.Chats"))
    }

    @Test
    fun theCloudRouteCannotBeSelected() {
        assertTrue(viewModel.contains("const val LAN = \"lan\""))
        assertTrue(viewModel.contains("const val CLOUD = \"cloud\""))
        // The guard lives in the view model, not only in the UI, so no future
        // call site can route at the vendor's cloud by accident.
        assertTrue(viewModel.contains("if (route != ApiRoute.LAN) {"))
        assertTrue(store.contains("ApiRoute.LAN"))
        // And the picker never claims the cloud option is usable.
        assertTrue(screen.contains("ApiRouteOption(ApiRoute.CLOUD, stringResource(R.string.devices_route_cloud), enabled = false)"))
        assertTrue(screen.contains("R.string.devices_route_cloud_unavailable"))
    }

    @Test
    fun cloudOnlyAccountActionsAreNotFaked() {
        // These authenticate against the vendor's cloud; rendering them would
        // mean pretending this build has a cloud account.
        assertFalse(screen.contains("devices_account_delete"))
        assertFalse(screen.contains("account-web-login"))
        assertFalse(screen.contains("有效至"))
        // The entitlement block uses the official "unavailable" branch instead.
        assertTrue(screen.contains("R.string.devices_account_entitlements_unavailable"))
    }

    @Test
    fun directConnectBehaviourIsIntact() {
        // The page must not have replaced the encrypted multi-instance store.
        assertTrue(store.contains("EncryptedSharedPreferences"))
        assertTrue(instances.contains("fun upsertInstance"))
        assertTrue(viewModel.contains("fun switchInstance(url: String)"))
        assertTrue(viewModel.contains("fun addDevice(connection: String, name: String, username: String, password: String)"))
        // Pairing reuses the existing sign-in rather than adding a second
        // credential path.
        assertTrue(viewModel.contains("login(url, username, password, remember = false, label = label)"))
    }
}
