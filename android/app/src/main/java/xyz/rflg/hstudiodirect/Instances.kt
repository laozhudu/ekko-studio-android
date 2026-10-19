package xyz.rflg.hstudiodirect

import org.json.JSONArray
import org.json.JSONObject

/**
 * The `.device-endpoint-tag` a direct connection carries.
 *
 * The official app reads `metadata.endpoint_kind` off the device record and
 * prints 桌面端/Web 端. A direct connection here always terminates at the Studio
 * HTTP/WebSocket surface rather than at a desktop agent, so `web` is what a
 * Studio saved through this app reports. It is stored per device, so a Studio
 * that does announce a desktop endpoint can override it later. Recorded as an
 * inference, not an extraction, in docs/parity/devices.md.
 */
internal const val DEFAULT_ENDPOINT_KIND = "web"

/**
 * One Hermes Studio deployment the user has signed in to.
 *
 * HStudio keeps the same list under `hermes-studio:local-devices`; here it lives
 * in the encrypted preferences, so the bearer token never lands on disk in the
 * clear and every studio the user owns is one tap away.
 *
 * [platform], [webUiVersion] and [endpointKind] mirror the three per-device
 * metadata fields the official `pages/devices` card prints in
 * `.device-system`, `.device-version-row` and `.device-endpoint-tag`
 * (`metadata.os`, `metadata.hermes_web_ui_version`, `metadata.endpoint_kind`).
 * They are filled in by a reachability pass rather than typed by the user, and
 * default to blank so a record written by an older build still loads.
 */
data class StudioInstance(
    val url: String,
    val label: String,
    val username: String,
    val token: String,
    val lastUsedAt: Long,
    /** Server OS family, e.g. `linux`; rendered as Apple/Windows/Linux. */
    val platform: String = "",
    /** Active Studio web-client version, e.g. `0.7.13`. */
    val webUiVersion: String = "",
    /** `desktop` or `web`, matching the official `metadata.endpoint_kind`. */
    val endpointKind: String = "",
    /** Unix seconds of the last successful reachability probe. */
    val lastSeenAt: Long = 0,
) {
    /** Host shown under the label, e.g. `hermes.example.com:8446`. */
    val host: String get() = hostLabel(url)
}

internal fun hostLabel(url: String): String =
    url.removePrefix("https://").removePrefix("http://").trimEnd('/').substringBefore('/')

internal fun instancesToJson(list: List<StudioInstance>): String {
    val array = JSONArray()
    list.forEach { item ->
        array.put(
            JSONObject()
                .put("url", item.url)
                .put("label", item.label)
                .put("username", item.username)
                .put("token", item.token)
                .put("lastUsedAt", item.lastUsedAt)
                .put("platform", item.platform)
                .put("webUiVersion", item.webUiVersion)
                .put("endpointKind", item.endpointKind)
                .put("lastSeenAt", item.lastSeenAt),
        )
    }
    return array.toString()
}

internal fun instancesFromJson(raw: String): List<StudioInstance> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").trim().trimEnd('/')
            if (url.isBlank()) return@mapNotNull null
            StudioInstance(
                url = url,
                label = item.optString("label").ifBlank { hostLabel(url) },
                username = item.optString("username"),
                token = item.optString("token"),
                lastUsedAt = item.optLong("lastUsedAt"),
                // Absent on records written before the devices parity pass.
                platform = item.optString("platform"),
                webUiVersion = item.optString("webUiVersion"),
                endpointKind = item.optString("endpointKind"),
                lastSeenAt = item.optLong("lastSeenAt"),
            )
        }
    }.getOrElse { emptyList() }
}

/** Newest first; the same URL is replaced rather than duplicated. */
internal fun upsertInstance(list: List<StudioInstance>, item: StudioInstance): List<StudioInstance> =
    (listOf(item) + list.filterNot { it.url == item.url }).sortedByDescending { it.lastUsedAt }

// ── official `pages/devices` display helpers ──────────────────────────────
//
// These are ports of the minified render helpers in the APK's app-service.js
// (page scope `data-v-61a2f361`). They are plain functions rather than inline
// expressions in the composable so the parity test can assert on them without a
// Compose runtime — this repo has no instrumentation tests.

/** Official `it(name)`: the card clamps the name at 40 characters, then ellipsis. */
internal fun deviceDisplayName(raw: String): String {
    val chars = raw.trim().toCharArray()
    return if (chars.size > 40) chars.take(40).joinToString("") + "..." else chars.joinToString("")
}

/**
 * Official `Xe(device)`: maps the server's `metadata.os` onto an OS family, and
 * falls back to the literal product name when the Studio does not report one.
 * The official matcher tests name/platform/system/type in that order.
 */
internal fun deviceSystemLabel(os: String): String {
    val probe = os.trim().lowercase()
    return when {
        os.isBlank() -> ""
        Regex("darwin|macos|mac os|osx").containsMatchIn(probe) -> "Apple"
        Regex("win32|windows").containsMatchIn(probe) -> "Windows"
        Regex("linux").containsMatchIn(probe) -> "Linux"
        else -> ""
    }
}

/** Official `nt(device)` / `at(device)`: `metadata.endpoint_kind` badge. */
internal fun deviceEndpointKind(raw: String): String = when (raw.trim().lowercase()) {
    "desktop" -> "desktop"
    "web" -> "web"
    else -> ""
}

/** Which relative-time bucket the official `rt(device)` footer line falls in. */
internal enum class DeviceAge { Online, Never, JustNow, Minutes, Hours, Days }

/**
 * Official `rt(device)`'s branch selection, split out from its text so the
 * caller can resolve the string resource (a `@Composable` call) rather than
 * having translations handed into a non-composable helper.
 *
 * [nowSeconds] is injected so the buckets are testable.
 */
internal fun deviceAge(online: Boolean, lastSeenAt: Long, nowSeconds: Long): DeviceAge {
    if (online) return DeviceAge.Online
    if (lastSeenAt <= 0) return DeviceAge.Never
    val delta = (nowSeconds - lastSeenAt).coerceAtLeast(0)
    return when {
        delta < 60 -> DeviceAge.JustNow
        delta < 3600 -> DeviceAge.Minutes
        delta < 86_400 -> DeviceAge.Hours
        else -> DeviceAge.Days
    }
}

/** The count that goes into the bucket's `%d` placeholder; 0 for the fixed ones. */
internal fun deviceAgeCount(age: DeviceAge, lastSeenAt: Long, nowSeconds: Long): Long {
    val delta = (nowSeconds - lastSeenAt).coerceAtLeast(0)
    return when (age) {
        DeviceAge.Minutes -> delta / 60
        DeviceAge.Hours -> delta / 3600
        DeviceAge.Days -> delta / 86_400
        else -> 0
    }
}

/**
 * Normalises whatever the user pasted into the official `.connection-input`.
 *
 * This is a port of the official `Aa(connection)` origin extractor: the field
 * takes a whole pasted payload (maxlength 2048) that may carry the real address
 * in a `connectionUrl=` / `url=` querystring entry, and the app reduces it to a
 * scheme + host + port. Keeping the official behaviour matters here because a
 * LAN Studio is usually plain `http://192.168.x.x:port`, and upgrading that to
 * https — which the sign-in field does — would fail to connect.
 *
 * Two deliberate additions, both recorded in docs/parity/devices.md: the
 * official regex requires a scheme, and a bare `host:port` typed by hand is
 * accepted here (plain http for an IP/`.local`/explicit port, https otherwise).
 */
internal fun normalizeDeviceConnection(raw: String): String? {
    var text = raw.trim()
    if (text.isBlank()) return null

    // A pasted payload often hides the address in a querystring or hash.
    Regex("""[?&#](?:connectionUrl|connection_url|connection|url)=([^&#]+)""", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues?.get(1)
        ?.let { text = runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    Regex("""https?://(\[[0-9a-fA-F:]+\]|[^\s/?#:@]+)(?::(\d{1,5}))?""", RegexOption.IGNORE_CASE)
        .find(text)
        ?.let { match ->
            val scheme = if (match.value.lowercase().startsWith("https://")) "https" else "http"
            val host = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull()
            if (match.groupValues[2].isNotEmpty() && (port == null || port !in 1..65535)) return null
            return "$scheme://$host" + if (port != null) ":$port" else ""
        }

    // No scheme: accept a bare host[:port], guessing the scheme the way a LAN
    // deployment actually behaves.
    val bare = Regex("""^([A-Za-z0-9.\-]+|\[[0-9a-fA-F:]+\])(?::(\d{1,5}))?$""").find(text) ?: return null
    val host = bare.groupValues[1]
    val port = bare.groupValues[2].toIntOrNull()
    if (bare.groupValues[2].isNotEmpty() && (port == null || port !in 1..65535)) return null
    val lan = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host) ||
        host.endsWith(".local") ||
        port != null
    return (if (lan) "http://" else "https://") + host + if (port != null) ":$port" else ""
}
