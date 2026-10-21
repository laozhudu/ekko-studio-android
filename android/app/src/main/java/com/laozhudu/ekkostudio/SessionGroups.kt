package com.laozhudu.ekkostudio

/**
 * The official client's session grouping, as a pure function.
 *
 * Extracted from the shipped `pages/index` bundle, where the group list is
 * built as:
 *
 *     [{key:"pinned",  label:"置顶", collapsible:false},
 *      {key:"recent",  label:"最近", collapsible:true},
 *      ...categories.map(c => {key:"category-"+c.id, label:c.name, collapsible:true}),
 *      {key:"category-none", label:"未分类", collapsible:true}]
 *
 * and membership is:
 *
 *  - `pinned`        → `is_pinned` (and only this group)
 *  - `recent`        → not pinned, capped at 10
 *  - `category-<id>` → not pinned and `category_id == id` (all of them)
 *  - `category-none` → not pinned and no category, or a category that no longer exists
 *
 * A session therefore appears twice when it is unpinned: once in `recent`, once
 * in its category. That is deliberate — the official list works that way, and it
 * is also what makes "recent" useful as a short list.
 *
 * Empty groups are dropped, matching `.filter(e => e.sessions.length || e.hasMore)`.
 */
data class SessionGroupSpec(
    val key: String,
    val label: String,
    val collapsible: Boolean,
)

/** How many unpinned sessions the `recent` group shows before its categories take over. */
const val RECENT_GROUP_LIMIT = 10

const val GROUP_PINNED = "pinned"
const val GROUP_RECENT = "recent"
const val GROUP_NO_CATEGORY = "category-none"

fun categoryGroupKey(id: Int): String = "category-$id"

/**
 * The ordered group list. Whether a group is currently collapsed is UI state and
 * deliberately not part of this model.
 */
fun officialSessionGroups(
    categories: List<SessionCategory>,
): List<SessionGroupSpec> = buildList {
    add(SessionGroupSpec(GROUP_PINNED, "置顶", collapsible = false))
    add(SessionGroupSpec(GROUP_RECENT, "最近", collapsible = true))
    categories.forEach { add(SessionGroupSpec(categoryGroupKey(it.id), it.name, collapsible = true)) }
    add(SessionGroupSpec(GROUP_NO_CATEGORY, "未分类", collapsible = true))
}

/**
 * Members of one group, newest first.
 *
 * `updatedAt` is an ISO string from the server; lexicographic order matches
 * chronological order for that format, which is why it sorts directly.
 */
fun sessionsInGroup(
    sessions: List<SessionSummary>,
    categories: List<SessionCategory>,
    key: String,
): List<SessionSummary> {
    val known = categories.map { it.id }.toSet()
    val matching = when {
        key == GROUP_PINNED -> sessions.filter { it.pinned }
        key == GROUP_RECENT -> sessions.filterNot { it.pinned }.take(RECENT_GROUP_LIMIT)
        key == GROUP_NO_CATEGORY -> sessions.filterNot { it.pinned }
            .filter { it.categoryId == null || it.categoryId !in known }
        key.startsWith("category-") -> {
            // Only a category that still exists owns a group. A session whose
            // category was deleted belongs to 未分类 and must not be reachable
            // through the dead key as well.
            val id = key.removePrefix("category-").toIntOrNull()?.takeIf { it in known }
            sessions.filterNot { it.pinned }.filter { id != null && it.categoryId == id }
        }
        else -> emptyList()
    }
    return matching.sortedWith(
        compareByDescending<SessionSummary> { it.updatedAt.orEmpty() }
            .thenByDescending { it.pinned },
    )
}

/** Groups that actually have something in them, in official order. */
fun populatedSessionGroups(
    sessions: List<SessionSummary>,
    categories: List<SessionCategory>,
): List<Pair<SessionGroupSpec, List<SessionSummary>>> =
    officialSessionGroups(categories)
        .map { spec -> spec to sessionsInGroup(sessions, categories, spec.key) }
        .filter { (_, members) -> members.isNotEmpty() }

/**
 * Which groups start collapsed.
 *
 * The official client collapses every group except the first one when the
 * category set changes, so the list opens short instead of showing every
 * category expanded at once.
 */
fun defaultExpandedGroupKeys(groups: List<SessionGroupSpec>): Set<String> =
    groups.firstOrNull()?.let { setOf(it.key) } ?: emptySet()
