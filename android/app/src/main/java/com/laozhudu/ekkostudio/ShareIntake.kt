package com.laozhudu.ekkostudio

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/**
 * A file shared into the app from somewhere else, waiting to be attached.
 *
 * The official client calls this 「待添加文件」 and its copy is explicit about the
 * lifetime: the app keeps *its own copy* and clearing the queue does not touch the
 * original file. That is the only honest way to do it — a content:// URI from
 * another app is not guaranteed to stay readable, so the bytes have to be copied
 * up front.
 */
data class PendingFile(
    /** The app's own copy, in the cache directory. Never the source file. */
    val file: File,
    val name: String,
    val mime: String,
    val size: Long,
)

/**
 * Reads `ACTION_SEND` / `ACTION_SEND_MULTIPLE` into [PendingFile]s.
 *
 * Kept out of the activity so the parsing rules can be tested: `EXTRA_STREAM` can
 * be a single URI or an array, `EXTRA_TEXT` may carry a caption that becomes part
 * of the message, and a sender may hand over a URI we cannot open at all — all of
 * which are normal, not errors.
 */
object ShareIntake {

    /**
     * The order-preserving de-duplication a share needs.
     *
     * String in, string out, so this is testable: `android.net.Uri` is a stub in
     * the JVM test runtime, and enabling `returnDefaultValues` to work around that
     * would make every other Android stub silently return null too.
     */
    fun dedupe(uris: List<String>): List<String> = uris.filter { it.isNotBlank() }.distinct()

    /** A caption is trimmed, and a blank one is no caption at all. */
    fun cleanCaption(raw: String?): String = raw?.trim().orEmpty()

    /** The URIs a share intent carries, in order, de-duplicated. */
    fun streamUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val single = @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val multiple = @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        val all = listOfNotNull(single) + multiple.orEmpty()
        // De-duplicated by string, then mapped back to the original Uri objects so
        // the caller never sees a re-parsed one.
        val keep = dedupe(all.map { it.toString() }).toSet()
        return all.filter { it.toString() in keep }.distinctBy { it.toString() }
    }

    /** A caption sent alongside the files, when the sender included one. */
    fun caption(intent: Intent?): String =
        cleanCaption(intent?.getStringExtra(Intent.EXTRA_TEXT))

    /** The display name a picker shows, falling back to the last path segment. */
    fun displayName(resolver: ContentResolver, uri: Uri): String {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "shared-${UUID.randomUUID().toString().take(6)}"
    }

    /**
     * Copies every readable stream into [targetDir].
     *
     * A URI that cannot be opened is skipped rather than aborting the whole share:
     * a ten-file share with one permission problem should still stage the other
     * nine.
     */
    fun stage(
        resolver: ContentResolver,
        uris: List<Uri>,
        targetDir: File,
        mimeOf: (Uri) -> String,
    ): List<PendingFile> {
        targetDir.mkdirs()
        return uris.mapNotNull { uri ->
            runCatching {
                val name = displayName(resolver, uri)
                val mime = mimeOf(uri)
                val destination = uniqueDestination(targetDir, name)
                resolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                PendingFile(
                    file = destination,
                    name = name,
                    mime = mime,
                    size = destination.length(),
                )
            }.getOrNull()
        }
    }

    /** `a.txt`, `a (2).txt`, `a (3).txt` — two shares of one name must not collide. */
    internal fun uniqueDestination(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var candidate = File(dir, name)
        var counter = 2
        while (candidate.exists()) {
            val suffix = if (extension.isEmpty()) " ($counter)" else " ($counter).$extension"
            candidate = File(dir, "$base$suffix")
            counter++
        }
        return candidate
    }

    /** Removes the app's copies. The originals belong to whoever shared them. */
    fun clear(files: List<PendingFile>) {
        files.forEach { pending -> runCatching { pending.file.delete() } }
    }
}
