package com.laozhudu.ekkostudio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Extensions the previewer opens as text. Anything else is an image or a download. */
internal val TEXT_PREVIEW_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "log",
    "csv", "tsv", "sh", "bash", "zsh", "py", "kt", "kts", "java", "js", "ts", "jsx", "tsx",
    "go", "rs", "rb", "php", "c", "h", "cpp", "hpp", "cs", "swift", "sql", "xml", "html",
    "css", "scss", "gradle", "properties", "env", "diff", "patch",
)

internal val IMAGE_PREVIEW_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")

internal fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

/** How a chat attachment should be opened. */
internal enum class FilePreviewKind { Text, Image, Download }

internal fun previewKindOf(fileName: String): FilePreviewKind = when (extensionOf(fileName)) {
    in TEXT_PREVIEW_EXTENSIONS -> FilePreviewKind.Text
    in IMAGE_PREVIEW_EXTENSIONS -> FilePreviewKind.Image
    else -> FilePreviewKind.Download
}

/**
 * A full-screen look at a chat attachment before deciding to keep it.
 *
 * Tapping a file card used to go straight to a system download, which is a poor
 * deal on a phone: you get a notification, a Downloads folder entry and a second
 * app to find it in. Text and images are shown here instead; only genuinely
 * unviewable files are handed to the download manager.
 */
@Composable
fun FilePreviewDialog(
    file: ChatFileLink,
    loadText: suspend (String) -> String?,
    loadImage: suspend (String) -> ByteArray?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val kind = remember(file.fileName) { previewKindOf(file.fileName) }
    var text by remember(file.path) { mutableStateOf<String?>(null) }
    var bitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(file.path) { mutableStateOf(kind != FilePreviewKind.Download) }
    var failed by remember(file.path) { mutableStateOf(false) }

    LaunchedEffect(file.path) {
        when (kind) {
            FilePreviewKind.Text -> {
                val loaded = runCatching { loadText(file.path) }.getOrNull()
                text = loaded
                failed = loaded == null
                loading = false
            }
            FilePreviewKind.Image -> {
                val bytes = runCatching { loadImage(file.path) }.getOrNull()
                bitmap = bytes?.let { data ->
                    runCatching { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }.getOrNull()
                }
                failed = bitmap == null
                loading = false
            }
            FilePreviewKind.Download -> loading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header: the file name is the only thing that must always be visible.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.fileName,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = inkTextPrimary(inkDark),
                        )
                        Text(
                            extensionOf(file.fileName).ifBlank { stringResource(R.string.file_preview_unknown) }
                                .uppercase(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = inkTextMuted(inkDark),
                        )
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, stringResource(R.string.action_download), Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_close), Modifier.size(20.dp))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator()
                        kind == FilePreviewKind.Image && bitmap != null -> Image(
                            bitmap = bitmap!!,
                            contentDescription = file.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        kind == FilePreviewKind.Text && text != null -> Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = inkCodeBg(inkDark),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    // A 2 MB log would freeze the composer; the tail is
                                    // what matters when someone shares a log file.
                                    text = text!!.takeLast(PREVIEW_CHAR_LIMIT),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(10.dp),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = inkCodeText(inkDark),
                                )
                            }
                        }
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (failed) R.string.file_preview_failed else R.string.file_preview_download_only,
                                ),
                                fontSize = 12.sp,
                                color = inkTextMuted(inkDark),
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            TextButton(onClick = onDownload) {
                                Text(stringResource(R.string.action_download))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Characters kept for the text preview; the tail of a log is the useful part. */
internal const val PREVIEW_CHAR_LIMIT = 120_000
