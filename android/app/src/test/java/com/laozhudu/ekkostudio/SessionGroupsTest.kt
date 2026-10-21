package com.laozhudu.ekkostudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P2: the session list is grouped the way the official client groups it.
 *
 * The rules were read out of the shipped bundle: 置顶 (not collapsible), 最近
 * (capped at 10), one group per category, then 未分类. A pinned session appears
 * only in 置顶; an unpinned one appears twice (in 最近 and in its category),
 * which is what makes 最近 a usable short list rather than a duplicate of the
 * whole database.
 */
class SessionGroupsTest {
    private val categories = listOf(
        SessionCategory(2, "PVE"),
        SessionCategory(4, "NAS"),
    )

    private fun session(
        id: String,
        pinned: Boolean = false,
        categoryId: Int? = null,
        updatedAt: String = "2026-10-01T10:00:00Z",
    ) = SessionSummary(
        id = id,
        title = id,
        model = "m",
        updatedAt = updatedAt,
        categoryId = categoryId,
        pinned = pinned,
    )

    @Test
    fun `group order is pinned recent then each category then uncategorised`() {
        val groups = officialSessionGroups(categories)

        assertEquals(
            listOf(GROUP_PINNED, GROUP_RECENT, "category-2", "category-4", GROUP_NO_CATEGORY),
            groups.map { it.key },
        )
        // Only 置顶 is fixed open; everything else can be folded away.
        assertEquals(false, groups.first().collapsible)
        assertTrue(groups.drop(1).all { it.collapsible })
    }

    @Test
    fun `a pinned session lives only in the pinned group`() {
        val sessions = listOf(session("p", pinned = true, categoryId = 4))

        assertEquals(listOf("p"), sessionsInGroup(sessions, categories, GROUP_PINNED).map { it.id })
        // Not in its category and not in recent: pinning takes it out of both.
        assertTrue(sessionsInGroup(sessions, categories, "category-4").isEmpty())
        assertTrue(sessionsInGroup(sessions, categories, GROUP_RECENT).isEmpty())
    }

    @Test
    fun `an unpinned session appears in recent and in its own category`() {
        val sessions = listOf(session("a", categoryId = 4))

        assertEquals(listOf("a"), sessionsInGroup(sessions, categories, GROUP_RECENT).map { it.id })
        assertEquals(listOf("a"), sessionsInGroup(sessions, categories, "category-4").map { it.id })
        assertTrue(sessionsInGroup(sessions, categories, "category-2").isEmpty())
    }

    @Test
    fun `recent stops at ten, categories do not`() {
        val sessions = (1..15).map {
            session("s$it", categoryId = 4, updatedAt = "2026-10-%02dT10:00:00Z".format(it))
        }

        assertEquals(RECENT_GROUP_LIMIT, sessionsInGroup(sessions, categories, GROUP_RECENT).size)
        assertEquals(15, sessionsInGroup(sessions, categories, "category-4").size)
    }

    @Test
    fun `recent is newest first`() {
        val sessions = listOf(
            session("old", updatedAt = "2026-09-01T00:00:00Z"),
            session("new", updatedAt = "2026-10-20T00:00:00Z"),
            session("mid", updatedAt = "2026-10-10T00:00:00Z"),
        )

        assertEquals(
            listOf("new", "mid", "old"),
            sessionsInGroup(sessions, categories, GROUP_RECENT).map { it.id },
        )
    }

    @Test
    fun `a deleted category turns its sessions into uncategorised`() {
        // The category list no longer contains id 9, but a session still carries it.
        val sessions = listOf(session("orphan", categoryId = 9), session("none"))

        assertEquals(
            listOf("none", "orphan"),
            sessionsInGroup(sessions, categories, GROUP_NO_CATEGORY).map { it.id }.sorted(),
        )
        assertTrue(sessionsInGroup(sessions, categories, "category-9").isEmpty())
    }

    @Test
    fun `empty groups are dropped but the pinned group survives when it has members`() {
        val sessions = listOf(session("p", pinned = true))

        val populated = populatedSessionGroups(sessions, categories)

        assertEquals(listOf(GROUP_PINNED), populated.map { it.first.key })
        // 最近/分类/未分类 are all empty here, so they are not rendered at all.
        assertEquals(1, populated.size)
    }

    @Test
    fun `only the first group starts expanded`() {
        assertEquals(
            setOf(GROUP_PINNED),
            defaultExpandedGroupKeys(officialSessionGroups(categories)),
        )
    }

    /**
     * The run payload is the only place a brand-new conversation can be filed:
     * a session does not exist until a run creates it, so there is no id to
     * PATCH beforehand.
     */
    @Test
    fun `the run payload carries the category and the starting workspace`() {
        val socket = File("src/main/java/com/laozhudu/ekkostudio/ChatSocket.kt").readText()

        assertTrue(socket.contains("payload.put(\"category_id\", categoryId)"))
        assertTrue(socket.contains("payload.put(\"workspace\", workspace)"))
        // Guarded so an existing conversation never gets re-filed by a later run.
        val viewModel = File("src/main/java/com/laozhudu/ekkostudio/AppViewModel.kt").readText()
        assertTrue(viewModel.contains("newChatCategoryId.takeIf { openId == null }"))
    }

    @Test
    fun `search results carry the matched message so the transcript can land on it`() {
        val api = File("src/main/java/com/laozhudu/ekkostudio/HermesApi.kt").readText()
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

        assertTrue(api.contains("matchedMessageId = firstNonBlank(item, \"matched_message_id\")"))
        assertTrue(activity.contains("it.messageId == target"))
        assertTrue(activity.contains("viewModel.consumeFocusMessage()"))
    }

    @Test
    fun `the preview line only appears while searching`() {
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

        // Both call sites must be explicit about it; a default-only pass would let
        // the grouped list inherit the search behaviour.
        assertTrue(activity.contains("showPreview = true"))
        assertTrue(activity.contains("showPreview = false"))
        assertTrue(activity.contains("if (showPreview && preview.isNotBlank())"))
    }

    @Test
    fun `the drawer exists and offers the account level actions`() {
        val drawer = File("src/main/java/com/laozhudu/ekkostudio/AccountDrawer.kt").readText()
        val activity = File("src/main/java/com/laozhudu/ekkostudio/MainActivity.kt").readText()

        listOf("onDevices", "onSettings", "onAppearance", "onAbout", "onSignOut").forEach {
            assertTrue("drawer lost $it", drawer.contains(it))
        }
        assertTrue(activity.contains("ModalNavigationDrawer("))
        // A horizontal drag from the left edge would fight swipe-to-reply.
        assertTrue(activity.contains("gesturesEnabled = drawerState.isOpen"))
    }

    @Test
    fun `the drawer header shows the host, not the url`() {
        assertEquals("192.168.99.226:8648", serverHostOf("https://192.168.99.226:8648/"))
        assertEquals("example.test", serverHostOf("http://example.test"))
        assertEquals("", serverHostOf(""))
    }
}
