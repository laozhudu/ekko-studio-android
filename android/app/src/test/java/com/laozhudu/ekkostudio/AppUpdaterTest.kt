package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {
    private val installed = "v0.2.1"
    private val newer = "v0.2.2"
    private val url =
        "https://github.com/laozhudu/ekko-studio-android/releases/download/v0.2.2/ekko-studio-android.apk"

    @Test
    fun `newer release tag exposes the signed apk`() {
        val update = AppUpdater.parseRelease(
            """{"tag_name":"$newer","assets":[{"name":"ekko-studio-android.apk","browser_download_url":"$url"}]}""",
            installed,
        )

        assertEquals(newer, update?.tag)
        assertEquals(url, update?.downloadUrl)
    }

    @Test
    fun `same tag and non github downloads are rejected`() {
        val same = """{"tag_name":"$installed","assets":[{"name":"ekko-studio-android.apk","browser_download_url":"$url"}]}"""
        val unsafe = """{"tag_name":"$newer","assets":[{"name":"ekko-studio-android.apk","browser_download_url":"https://example.com/app.apk"}]}"""
        val missingAsset = """{"tag_name":"$newer","assets":[{"name":"other.apk","browser_download_url":"$url"}]}"""

        assertNull(AppUpdater.parseRelease(same, installed))
        assertNull(AppUpdater.parseRelease(unsafe, installed))
        assertNull(AppUpdater.parseRelease(missingAsset, installed))
    }
}
