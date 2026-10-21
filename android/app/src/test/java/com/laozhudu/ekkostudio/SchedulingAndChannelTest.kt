package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P7: the scheduled-job form, and the WeChat link code.
 *
 * The board already had columns, long-press drag and a move menu, so nothing was
 * rebuilt there — `KanbanColumn`/`KANBAN_STATUSES` are asserted instead.
 *
 * The cron rules are the server's own: four aliases, five fields, and each field a
 * comma list of `*`, `n`, `a-b`, optionally `/step`, inside a fixed range. That is
 * duplicated on purpose so a phone keyboard cannot produce an expression that only
 * fails after a round trip.
 */
class SchedulingAndChannelTest {

    // ── cron validation ──────────────────────────────────────────────────

    @Test
    fun `the presets the form offers are all valid`() {
        listOf(
            "* * * * *", "*/5 * * * *", "0 * * * *",
            "0 0 * * *", "0 9 * * *", "0 9 * * 1", "0 9 1 * *",
        ).forEach { expression ->
            assertNull("$expression should be valid", CronExpression.validate(expression))
        }
    }

    @Test
    fun `the server's aliases are understood`() {
        assertEquals("0 * * * *", CronExpression.ALIASES["@hourly"])
        assertEquals("0 0 * * *", CronExpression.normalize("@daily"))
        // Case-insensitive, as the server lowercases before looking up.
        assertEquals("0 0 * * 0", CronExpression.normalize("@WEEKLY"))
        assertNull(CronExpression.validate("@monthly"))
    }

    @Test
    fun `a field count other than five is rejected`() {
        assertEquals("schedule must be a five-field cron expression", CronExpression.validate("0 9 * *"))
        assertEquals("schedule must be a five-field cron expression", CronExpression.validate("0 0 9 * * *"))
        assertEquals("schedule must be a five-field cron expression", CronExpression.validate(""))
    }

    @Test
    fun `each field is bounded by its own range`() {
        // The extremes of every range are allowed.
        assertNull(CronExpression.validate("59 23 31 12 6"))
        // One past each extreme is not.
        assertEquals("minute 60", "invalid cron field", CronExpression.validate("60 * * * *"))
        assertEquals("hour 24", "invalid cron field", CronExpression.validate("0 24 * * *"))
        assertEquals("day 0", "invalid cron field", CronExpression.validate("0 0 0 * *"))
        assertEquals("day 32", "invalid cron field", CronExpression.validate("0 0 32 * *"))
        assertEquals("month 13", "invalid cron field", CronExpression.validate("0 0 1 13 *"))
        assertEquals("weekday 7", "invalid cron field", CronExpression.validate("0 0 * * 7"))
    }

    @Test
    fun `ranges steps and comma lists follow the server's grammar`() {
        assertNull(CronExpression.validate("0-30 * * * *"))
        assertNull(CronExpression.validate("*/15 9-17 * * 1-5"))
        assertNull(CronExpression.validate("0,30 * * * *"))
        assertEquals("invalid cron field", CronExpression.validate("30-10 * * * *"))
        assertEquals("invalid cron field", CronExpression.validate("*/0 * * * *"))
        assertEquals("invalid cron field", CronExpression.validate("0 9 * * MON"))
    }

    @Test
    fun `the plain-language reading only covers shapes it can be sure about`() {
        assertEquals("every 5 minutes", CronExpression.describeSchedule("*/5 * * * *"))
        assertEquals("daily at 09:00", CronExpression.describeSchedule("0 9 * * *"))
        assertEquals("Mondays at 09:00", CronExpression.describeSchedule("0 9 * * 1"))
        assertEquals("every hour at :00", CronExpression.describeSchedule("0 * * * *"))
        assertEquals("monthly on day 1 at 09:00", CronExpression.describeSchedule("0 9 1 * *"))
        assertNull("a complex expression gets no guess", CronExpression.describeSchedule("*/7 3-5 1,15 * 2"))
    }

    @Test
    fun `an alias is shown expanded, so the user sees what will run`() {
        assertEquals("@daily → 0 0 * * *", CronExpression.describe("@daily"))
        assertEquals("0 9 * * *", CronExpression.describe("0 9 * * *"))
    }

    @Test
    fun `the form refuses an invalid expression instead of sending it`() {
        val form = KotlinSource.funBody(File("src/main/java/com/laozhudu/ekkostudio/CronJobs.kt").readText(), "CronJobForm")!!

        assertTrue(form.contains("CronExpression.validate(schedule)"))
        assertTrue("the button is gated on the validation", form.contains("scheduleError == null"))
        assertTrue("and says why", form.contains("cron_bad_schedule"))
        assertTrue(form.contains("cron_needs_name"))
        assertTrue(form.contains("cron_needs_prompt"))
    }

    // ── board: already built, so asserted not rebuilt ────────────────────

    @Test
    fun `the board keeps its columns and its drag gesture`() {
        val screen = File("src/main/java/com/laozhudu/ekkostudio/KanbanScreens.kt").readText()

        assertTrue(screen.contains("KANBAN_STATUSES"))
        assertTrue(screen.contains("KanbanColumn("))
        assertTrue("long press, then drag horizontally", screen.contains("detectDragGesturesAfterLongPress"))
        assertTrue("dragging past a column moves the task", screen.contains("onMove(KANBAN_STATUSES[candidate])"))
        assertEquals(
            "the eight official statuses",
            listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done"),
            KANBAN_STATUSES,
        )
    }

    // ── WeChat link code ────────────────────────────────────────────────

    @Test
    fun `the link code is drawn in the app, not opened in a browser`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

        assertTrue(activity.contains("QrCode(payload)"))
        // A phone cannot scan its own screen, so bouncing the URL out is a dead end.
        assertFalse(
            "the weixin QR must not be handed to ACTION_VIEW",
            activity.contains("Intent(Intent.ACTION_VIEW, Uri.parse(qr.url))"),
        )
        assertTrue("expired codes can be reissued", activity.contains("channel_qr_refresh"))
    }

    @Test
    fun `the qr renderer is reachable from the channel screen`() {
        val workspace = File("src/main/java/com/laozhudu/ekkostudio/StudioWorkspaceScreens.kt").readText()

        assertTrue(workspace.contains("internal fun QrCode(value: String)"))
    }

    @Test
    fun `every new string exists in all three locales`() {
        listOf("values", "values-zh-rCN", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            listOf(
                "cron_needs_name", "cron_needs_prompt", "cron_bad_schedule",
                "cron_schedule_hint", "channel_qr_refresh",
            ).forEach { key ->
                assertTrue("$key missing from $locale", strings.contains("name=\"$key\""))
            }
        }
    }
}
