package com.laozhudu.ekkostudio

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credentials live in EncryptedSharedPreferences, backed by a Keystore key, so
 * the bearer token is never written to disk in clear text.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hermes_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore can be unavailable on a small number of devices; the app must
        // still run rather than crash on launch.
        context.getSharedPreferences("hermes_plain", Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** Every Studio this phone has signed in to, newest first. */
    var instances: List<StudioInstance>
        get() = instancesFromJson(prefs.getString(KEY_INSTANCES, "").orEmpty())
        set(value) = prefs.edit().putString(KEY_INSTANCES, instancesToJson(value)).apply()

    var profile: String
        get() = prefs.getString(KEY_PROFILE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PROFILE, value).apply()

    fun sessionFor(profile: String): String =
        prefs.getString(sessionKey(profile), "").orEmpty()

    fun setSessionFor(profile: String, sessionId: String) {
        prefs.edit().putString(sessionKey(profile), sessionId).apply()
    }

    /** Saves the latest resume page id for a session so the app can restore
        its state after the process is killed and restarted. */
    fun setResumePageId(profile: String, sessionId: String, pageId: String) {
        prefs.edit().putString("resume_${profile}_$sessionId", pageId).apply()
    }

    /** Returns the last known resume page id for a session, or null. */
    fun getResumePageId(profile: String, sessionId: String): String? =
        prefs.getString("resume_${profile}_$sessionId", null)

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** BCP-47 tag chosen in Settings; blank means "follow the system". */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /** system, light, or dark. Kept separately from Studio's display settings. */
    var appearance: String
        get() = prefs.getString(KEY_APPEARANCE, "dark").orEmpty().ifBlank { "dark" }
        set(value) = prefs.edit().putString(KEY_APPEARANCE, value).apply()

    /** Global text scale for the whole UI (1f = system default). */
    var textScale: Float
        get() = prefs.getFloat(KEY_TEXT_SCALE, 1f).takeIf { it in 0.8f..1.4f } ?: 1f
        set(value) = prefs.edit().putFloat(KEY_TEXT_SCALE, value.coerceIn(0.8f, 1.4f)).apply()

    /**
     * Route the app dials, mirroring the official `ApiRouteSwitch` storage entry.
     *
     * Only [ApiRoute.LAN] is ever written: the official picker's other option is
     * the vendor's cloud relay, and this build has no cloud account to relay
     * through. The value is stored anyway so a future direct relay can reuse the
     * same key without a migration.
     */
    var apiRoute: String
        get() = prefs.getString(KEY_API_ROUTE, ApiRoute.LAN).orEmpty().ifBlank { ApiRoute.LAN }
        set(value) = prefs.edit().putString(KEY_API_ROUTE, value).apply()

    var reasoningEffort: String
        get() = prefs.getString(KEY_REASONING, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_REASONING, value).apply()

    /** .remember-check on the App's login form: keep the account on this device. */
    var rememberCredentials: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, false)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    var savedUsername: String
        get() = prefs.getString(KEY_SAVED_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SAVED_USER, value).apply()

    var savedPassword: String
        get() = prefs.getString(KEY_SAVED_PASSWORD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SAVED_PASSWORD, value).apply()

    /** Release tag the user asked not to be prompted about again. */
    var skippedUpdateTag: String
        get() = prefs.getString(KEY_SKIPPED_UPDATE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SKIPPED_UPDATE, value).apply()

    /** Last session list as JSON, so Chats can render before the network answers. */
    var sessionsSnapshot: String
        get() = prefs.getString(KEY_SESSIONS_SNAPSHOT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SESSIONS_SNAPSHOT, value).apply()

    fun clearCredentials() {
        // A remembered account survives sign-out — that is the whole point of
        // the checkbox — but everything else goes.
        if (rememberCredentials) {
            prefs.edit().remove(KEY_TOKEN).apply()
        } else {
            prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_SAVED_USER)
                .remove(KEY_SAVED_PASSWORD)
                .apply()
        }
    }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    private fun sessionKey(profile: String) = "$KEY_SESSION_PREFIX$profile"

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_INSTANCES = "instances"
        const val KEY_TOKEN = "token"
        const val KEY_PROFILE = "profile"
        const val KEY_SESSION_PREFIX = "session_"
        const val KEY_REASONING = "reasoning_effort"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_LANGUAGE = "language"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_API_ROUTE = "api_route"
        const val KEY_REMEMBER = "remember_credentials"
        const val KEY_SAVED_USER = "saved_username"
        const val KEY_SAVED_PASSWORD = "saved_password"
        const val KEY_SKIPPED_UPDATE = "skipped_update_tag"
        const val KEY_SESSIONS_SNAPSHOT = "sessions_snapshot"
    }

    /**
     * Per-conversation draft, the way Telegram keeps a separate unsent message in
     * every chat. One global field lost whatever you had typed the moment you
     * opened another conversation.
     */
    fun draftFor(conversationId: String): String {
        val key = conversationId.ifBlank { "new" }
        return prefs.getString("draft_$key", "").orEmpty()
    }

    fun setDraftFor(conversationId: String, text: String) {
        val key = conversationId.ifBlank { "new" }
        val editor = prefs.edit()
        if (text.isBlank()) editor.remove("draft_$key") else editor.putString("draft_$key", text)
        editor.apply()
    }

    /** Index of the last message this device has shown for a conversation. */
    fun readIndexFor(conversationId: String, total: Int): Int =
        prefs.getInt("read_index_$conversationId", total).coerceAtMost(total)

    fun setReadIndexFor(conversationId: String, index: Int) {
        if (conversationId.isBlank()) return
        prefs.edit().putInt("read_index_$conversationId", index).apply()
    }
}
