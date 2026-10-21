package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P1 of the module parity plan: the shell around the chat.
 *
 * These are contract/geometry assertions rather than screenshots, because a
 * screenshot proves what one build looked like and nothing about the next.
 */
class ShellParityTest {
    private val main = File("src/main/java/com/laozhudu/ekkostudio")
    private fun read(name: String) = File(main, name).readText()
    private val strings = File("src/main/res/values/strings.xml").readText()
    private val zh = File("src/main/res/values-zh-rCN/strings.xml").readText()

    @Test
    fun `about page mirrors the official layout`() {
        val about = read("AboutScreen.kt")

        // .about-brand: 80dp mark with a 20dp radius, 24sp/650 name, 13sp version.
        assertTrue(about.contains("size(80.dp)"))
        assertTrue(about.contains("RoundedCornerShape(20.dp)"))
        assertTrue(about.contains("fontSize = 24.sp"))
        assertTrue(about.contains("FontWeight(650)"))
        assertTrue(about.contains("fontSize = 13.sp"))
        // .about-card: 16dp radius, 1dp hairline, rows at 16/18.
        assertTrue(about.contains("RoundedCornerShape(16.dp)"))
        assertTrue(about.contains("border(1.dp, inkBorder(inkDark)"))
        assertTrue(about.contains("vertical = 16.dp"))
        assertTrue(about.contains("horizontal = 18.dp"))
    }

    @Test
    fun `about page reports the three versions the app actually depends on`() {
        val about = read("AboutScreen.kt")

        assertTrue(about.contains("BuildConfig.VERSION_NAME"))
        assertTrue(about.contains("state.serverVersions?.studio"))
        assertTrue(about.contains("state.serverVersions?.agent"))
    }

    @Test
    fun `the about page does not claim vendor affiliation`() {
        val note = zh.substringAfter("name=\"settings_about_note\">").substringBefore("<")

        assertTrue("the note must say it is unofficial", note.contains("非官方"))
        assertTrue("licence text must name the upstream project", read("AboutScreen.kt").contains("hermes-studio-mobile"))
    }

    @Test
    fun `onboarding has a background survival page with a settings shortcut`() {
        val intro = read("Intro.kt")

        assertTrue("no background page", intro.contains("intro_background_title"))
        assertTrue("no notification point", intro.contains("R.string.intro_background_notify"))
        assertTrue("no battery point", intro.contains("R.string.intro_background_battery"))
        assertTrue("no autostart point", intro.contains("R.string.intro_background_autostart"))
        assertTrue("no shortcut row", intro.contains("intro_background_open"))
        // The shortcut must open the real system settings screen, not a fake dialog.
        assertTrue(intro.contains("ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue(intro.contains("context.packageName"))
    }

    @Test
    fun `every intro page still resolves to a string resource`() {
        val intro = read("Intro.kt")
        val pages = Regex("IntroPage\\(([^)]*)\\)", RegexOption.DOT_MATCHES_ALL).findAll(intro)
            .map { it.groupValues[1] }
            // The data-class declaration also matches the pattern; an actual
            // page always names an icon.
            .filter { it.contains("icon = ") }
            .toList()

        assertTrue("expected at least four intro pages, found ${pages.size}", pages.size >= 4)
        pages.forEach { page ->
            assertTrue("a page has no title", page.contains("title = R.string."))
            assertTrue("a page has no body", page.contains("body = R.string."))
        }
    }

    @Test
    fun `the device card marks which studio this phone is talking to`() {
        val devices = read("DevicesScreen.kt")

        assertTrue("no current-device badge", devices.contains("R.string.devices_tag_current"))
        // It must be driven by the active URL, not by list position.
        assertTrue(devices.contains("if (active) {"))
        assertTrue(strings.contains("name=\"devices_tag_current\""))
        assertTrue(zh.contains("name=\"devices_tag_current\">当前设备"))
    }

    @Test
    fun `the route picker explains why relay modes are unavailable, not just that they are`() {
        val devices = read("DevicesScreen.kt")
        val reason = zh.substringAfter("name=\"devices_route_cloud_unavailable\">").substringBefore("<")

        assertTrue("the picker lost its disabled cloud option", devices.contains("enabled = false"))
        assertTrue("the reason must name the missing token", reason.contains("配对令牌"))
        assertTrue("the reason must name P2P too", reason.contains("P2P"))
        assertTrue("the reason must say self-hosted cannot issue it", reason.contains("自建"))
    }

    @Test
    fun `all three locales stay in lockstep`() {
        fun keys(path: String) = Regex("<string name=\"([^\"]+)\"")
            .findAll(File("src/main/res/$path/strings.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

        val en = keys("values")
        assertEquals("en/zh key sets diverge", en, keys("values-zh-rCN"))
        assertEquals("en/ar key sets diverge", en, keys("values-ar"))
    }

    @Test
    fun `the P1 additions are present in all three locales`() {
        listOf("about_check_update", "about_licences", "devices_tag_current",
               "intro_background_title", "intro_background_open").forEach { key ->
            listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
                assertTrue(
                    "$key missing from $locale",
                    File("src/main/res/$locale/strings.xml").readText().contains("name=\"$key\""),
                )
            }
        }
    }
}
