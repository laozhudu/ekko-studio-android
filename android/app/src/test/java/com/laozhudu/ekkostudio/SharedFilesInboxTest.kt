package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「待添加文件」 — the official `.inbox-*` block.
 *
 * A correction worth recording: the module inventory listed this as a
 * *notification* inbox. Reading the official copy settled it — 「待添加文件」,
 * 「选择文件添加到当前聊天，确认发送后才会上传」, 「仅移除 App 暂存的副本，不会删除原文件」
 * — it is the queue of files shared in from other apps, not a notification centre.
 * Building a notification list would have been inventing a feature.
 */
class SharedFilesInboxTest {

    // ── intake rules ─────────────────────────────────────────────────────

    // `android.content.Intent` and `android.net.Uri` are stubs in the JVM test
    // runtime, so the rules that matter are pure string functions and are tested
    // as such. Turning on returnDefaultValues to test the Intent adapter instead
    // would make every other Android stub silently return null.

    @Test
    fun `streams keep their order and drop duplicates`() {
        assertEquals(
            listOf("content://p/2", "content://p/1"),
            ShareIntake.dedupe(listOf("content://p/2", "content://p/1", "content://p/2")),
        )
    }

    @Test
    fun `a blank uri entry is not a file`() {
        assertEquals(listOf("content://p/1"), ShareIntake.dedupe(listOf("", "content://p/1", "   ")))
        assertEquals(emptyList<String>(), ShareIntake.dedupe(emptyList()))
    }

    @Test
    fun `a caption is trimmed, and a blank one is no caption`() {
        assertEquals("look at this", ShareIntake.cleanCaption("  look at this \n"))
        assertEquals("", ShareIntake.cleanCaption("   "))
        assertEquals("", ShareIntake.cleanCaption(null))
    }

    @Test
    fun `two files with the same name do not overwrite each other`() {
        val dir = File.createTempFile("inbox", "").let { file -> file.delete(); file.mkdirs(); file }
        try {
            val first = ShareIntake.uniqueDestination(dir, "photo.jpg")
            first.writeText("a")
            val second = ShareIntake.uniqueDestination(dir, "photo.jpg")
            second.writeText("b")

            assertEquals("photo.jpg", first.name)
            assertEquals("photo (2).jpg", second.name)
            assertEquals("a", first.readText())
            assertEquals("b", second.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a name without an extension still gets a suffix`() {
        val dir = File.createTempFile("inbox", "").let { file -> file.delete(); file.mkdirs(); file }
        try {
            ShareIntake.uniqueDestination(dir, "Makefile").writeText("x")

            assertEquals("Makefile (2)", ShareIntake.uniqueDestination(dir, "Makefile").name)
        } finally {
            dir.deleteRecursively()
        }
    }

    // ── manifest ─────────────────────────────────────────────────────────

    @Test
    fun `the activity accepts shares and stays single instance`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.intent.action.SEND_MULTIPLE"))
        assertTrue(manifest.contains("android.intent.action.SEND\""))
        // Without singleTask a share opens a second activity with no chat selected.
        assertTrue(manifest.contains("android:launchMode=\"singleTask\""))
    }

    // ── behaviour ────────────────────────────────────────────────────────

    @Test
    fun `the drawer copies the file, and clearing removes only that copy`() {
        val intake = File("src/main/java/com/laozhudu/ekkostudio/ShareIntake.kt").readText()
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()

        assertTrue("the share is copied into our cache", intake.contains("input.copyTo(output)"))
        assertTrue("clearing deletes only our file", intake.contains("pending.file.delete()"))
        // The promise the official copy makes has to be true in code.
        assertTrue(vm.contains("ShareIntake.clear(listOf(pending))"))
    }

    @Test
    fun `a failed upload keeps the file instead of dropping it`() {
        val vm = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
        val attach = KotlinSource.funBody(vm, "attachPendingFiles")!!

        assertTrue("failures are collected", attach.contains("failed += pending"))
        assertTrue("and stay in the queue", attach.contains("pendingFiles = failed"))
        assertTrue("with a notice, not silence", attach.contains("inbox_add_failed"))
    }

    @Test
    fun `attaching needs an open conversation`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

        assertTrue(activity.contains("canAttach = state.openSession != null"))
        assertTrue("the sheet must say why it is disabled", activity.contains("PendingFilesSheet("))
        val sheet = File("src/main/java/com/laozhudu/ekkostudio/PendingFilesSheet.kt").readText()
        assertTrue(sheet.contains("inbox_need_chat"))
    }

    @Test
    fun `the drawer matches the official measurements`() {
        val sheet = File("src/main/java/com/laozhudu/ekkostudio/PendingFilesSheet.kt").readText()

        assertTrue("17sp title", sheet.contains("fontSize = 17.sp"))
        assertTrue("13sp count", sheet.contains("fontSize = 13.sp"))
        assertTrue("12/19 help text", sheet.contains("lineHeight = 19.sp"))
        assertTrue("18dp checkbox", sheet.contains("size(18.dp)"))
        assertTrue("6dp checkbox radius", sheet.contains("RoundedCornerShape(6.dp)"))
        assertTrue("12dp row radius", sheet.contains("RoundedCornerShape(12.dp)"))
        assertTrue("46dp add button", sheet.contains("heightIn(min = 46.dp)"))
        assertTrue("13dp add radius", sheet.contains("RoundedCornerShape(13.dp)"))
        assertTrue("32x4 handle", sheet.contains("width = 32.dp, height = 4.dp"))
        assertTrue("14sp file name", sheet.contains("fontSize = 14.sp"))
        assertTrue("11sp meta", sheet.contains("fontSize = 11.sp"))
    }

    @Test
    fun `every new string exists in all three locales`() {
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            listOf("inbox_title", "inbox_help", "inbox_add_to_chat", "inbox_need_chat", "inbox_footer_help").forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the queue is described honestly in the module inventory`() {
        val plan = File("../../docs/official-parity-plan.md").readText()

        assertTrue("the plan must stop calling this a notification inbox", plan.contains("待添加文件"))
    }
}
