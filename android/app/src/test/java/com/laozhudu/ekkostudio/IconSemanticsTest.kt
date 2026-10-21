package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Protects the mobile icon language from falling back to generic placeholders. */
class IconSemanticsTest {

    private val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()
    private val studioSettings = File("src/main/java/com/laozhudu/ekkostudio/StudioSettings.kt").readText()
    private val drawableDir = File("src/main/res/drawable")

    @Test
    fun everyStudioChannelHasItsOwnNamedVectorAsset() {
        val expected = listOf(
            "telegram", "discord", "slack", "whatsapp", "matrix",
            "weixin", "wecom", "feishu", "dingtalk", "qqbot",
        )
        assertEquals(expected, CHANNELS.map { it.platform })
        assertEquals("icons must not accidentally share a drawable id", CHANNELS.size, CHANNELS.map { it.iconRes }.toSet().size)

        expected.forEach { platform ->
            val file = File(drawableDir, "ic_channel_${platform}.xml")
            assertTrue("missing icon for $platform", file.isFile)
            val vector = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            assertEquals("vector", vector.tagName)
            assertEquals("24", vector.getAttribute("android:viewportWidth"))
            assertEquals("24", vector.getAttribute("android:viewportHeight"))
            val path = vector.getElementsByTagName("path").item(0) as? Element
            assertNotNull("$platform vector has no path", path)
            assertTrue("$platform vector path is empty", path!!.getAttribute("android:pathData").isNotBlank())
        }
    }

    @Test
    fun channelScreensRenderBrandAssetsInsteadOfOneGenericHub() {
        val channels = activity.substringAfter("private fun ChannelsScreen")
            .substringBefore("/** One channel")
        val channel = activity.substringAfter("private fun ChannelScreen")
            .substringBefore("@Composable\ninternal fun SettingsSection")

        assertTrue(channels.contains("painterResource(spec.iconRes)"))
        assertTrue(channel.contains("painterResource(spec.iconRes)"))
        assertFalse(channels.contains("Icons.Filled.Hub"))
        assertFalse(channel.contains("Icons.Filled.Hub"))
    }

    @Test
    fun settingRowsRequireAContextualIcon() {
        assertTrue(studioSettings.contains("private fun StudioToggle(icon: ImageVector"))
        assertTrue(studioSettings.contains("private fun StudioNumber(\n    icon: ImageVector"))
        assertTrue(studioSettings.contains("private fun StudioChoice(\n    icon: ImageVector"))
        assertFalse("the old sun placeholder must not represent models", activity.contains("Icons.Filled.WbSunny"))
        assertFalse("settings must not reuse the old sun placeholder", studioSettings.contains("Icons.Filled.WbSunny"))
    }

    /**
     * The launcher mark is drawn by hand for this app.
     *
     * It used to be generated from the vendor's logo.png, which is brand artwork
     * inside a proprietary APK — fine for a private build, but this repository is
     * public. The replacement is a vector with its own path data, so there is no
     * extracted asset left to distribute, and the test fails if anyone brings one
     * back.
     */
    @Test
    fun launcherUsesOriginalArtwork() {
        val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml")
        val monochrome = File("src/main/res/drawable/ic_launcher_monochrome.xml")
        val adaptive = File("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val adaptiveRound = File("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()

        assertTrue("adaptive foreground must ship as a vector", foreground.isFile)
        assertTrue("monochrome variant must ship as a vector", monochrome.isFile)

        // Nothing extracted from the APK may sit in the resource tree.
        val leftoverPngs = File("src/main/res")
            .walkTopDown()
            .filter { it.isFile && it.name.startsWith("ic_launcher") && it.extension == "png" }
            .toList()
        assertTrue("vendor-derived launcher PNGs must not come back: $leftoverPngs", leftoverPngs.isEmpty())

        listOf("ic_launcher.xml" to adaptive, "ic_launcher_round.xml" to adaptiveRound).forEach { (name, text) ->
            assertTrue("$name must use the vector foreground", text.contains("@drawable/ic_launcher_foreground"))
            assertTrue("$name must declare a monochrome layer", text.contains("@drawable/ic_launcher_monochrome"))
        }

        listOf(foreground, monochrome).forEach { file ->
            val vector = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
            assertEquals("${file.name} must be a vector", "vector", vector.tagName)
            assertEquals("108", vector.getAttribute("android:viewportWidth"))
            val paths = vector.getElementsByTagName("path")
            assertTrue("${file.name} has no paths", paths.length >= 4)
            (0 until paths.length).forEach { index ->
                val path = paths.item(index) as Element
                assertTrue("${file.name} path $index is empty", path.getAttribute("android:pathData").isNotBlank())
            }
        }
    }
}
