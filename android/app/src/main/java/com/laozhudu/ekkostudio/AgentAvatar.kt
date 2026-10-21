package com.laozhudu.ekkostudio

import org.json.JSONObject

/**
 * The avatar payload a group agent accepts.
 *
 * Read out of the server's validator, which parses the field as JSON and accepts
 * exactly two shapes:
 *
 *     {"type":"generated","seed":"..."}            — rendered on the client
 *     {"type":"image","dataUrl":"data:image/(png|jpeg|webp);base64,..."}
 *
 * and rejects anything else, including an empty type. A `generated` avatar costs
 * no bytes at all, which is why it is the default when you ask for "another one"
 * — only an uploaded picture is stored inline.
 *
 * The whole string must stay under 1.5 M characters, so an uploaded image is
 * checked before it is encoded rather than after a round trip fails.
 */
object AgentAvatar {

    /** Formats the validator accepts for an inline image. */
    val ALLOWED_MIME = setOf("image/png", "image/jpeg", "image/webp")

    /** The server's own ceiling on the avatar field. */
    const val MAX_FIELD_CHARS = 1_500_000

    /** Raw bytes allowed before base64, leaving room for the wrapper. */
    const val MAX_IMAGE_BYTES = MAX_FIELD_CHARS / 4 * 3 - 1024

    fun generated(seed: String): String =
        JSONObject().put("type", "generated").put("seed", seed.take(200)).toString()

    /**
     * Null when the picture cannot be used: the caller shows an error instead of
     * sending something the server will reject.
     */
    fun image(mime: String, bytes: ByteArray): String? {
        if (mime !in ALLOWED_MIME) return null
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return null
        // java.util.Base64, not android.util.Base64: the latter is a stub in unit
        // tests and throws "not mocked", which would make this untestable. minSdk
        // is 26, where the java.util version exists on the device too.
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        val payload = "data:$mime;base64,$encoded"
        if (payload.length > MAX_FIELD_CHARS) return null
        return JSONObject().put("type", "image").put("dataUrl", payload).toString()
    }

    /** Reads back what a room agent carries, for the picker's preview. */
    fun parse(raw: String?): AvatarSpec? {
        if (raw.isNullOrBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return when (json.optString("type")) {
            "generated" -> json.optString("seed").takeIf { it.isNotBlank() }?.let {
                AvatarSpec(type = "generated", seed = it, dataUrl = null, updatedAt = 0)
            }
            "image" -> json.optString("dataUrl").takeIf { it.isNotBlank() }?.let {
                AvatarSpec(type = "image", seed = null, dataUrl = it, updatedAt = 0)
            }
            else -> null
        }
    }

    /** A seed that renders differently every time, without touching the network. */
    fun randomSeed(prefix: String = ""): String =
        (prefix.ifBlank { "agent" } + "-" + System.currentTimeMillis() + "-" +
            java.util.UUID.randomUUID().toString().take(8))
}
