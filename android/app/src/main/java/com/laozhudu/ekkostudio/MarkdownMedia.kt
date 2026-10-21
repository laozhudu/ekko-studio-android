package com.laozhudu.ekkostudio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * Fetches image bytes for the transcript.
 *
 * A markdown image in a chat message points at the Studio server
 * (`/api/studio/files/download?...`), which needs the bearer token — so the
 * platform cannot load it and a plain URL loader is not enough. The view model
 * installs the real implementation once; a test or preview can leave it as null
 * and the widget degrades to a caption.
 */
val LocalImageFetcher = compositionLocalOf<suspend (String) -> ByteArray?> { { null } }

/** Bytes already decoded this session, keyed by URL. Images repeat across turns. */
private val decodedImages = mutableMapOf<String, ImageBitmap?>()

@Composable
internal fun rememberRemoteImage(url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null
    var image by remember(url) { mutableStateOf(decodedImages[url]) }
    val fetcher = LocalImageFetcher.current
    LaunchedEffect(url) {
        if (decodedImages.containsKey(url)) {
            image = decodedImages[url]
            return@LaunchedEffect
        }
        val bytes = runCatching { fetcher(url) }.getOrNull()
        val bitmap = bytes?.let { data ->
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }.getOrNull()
        }
        // A failed load is remembered too, so a broken image is not re-requested
        // on every recomposition.
        decodedImages[url] = bitmap
        image = bitmap
    }
    return image
}

/**
 * An image block inside a message: capped height, tap to open full screen.
 *
 * The cap matters: a screenshot pasted into a chat is 1080×2400 and would
 * otherwise make one message taller than the whole viewport.
 */
@Composable
internal fun MarkdownImage(alt: String, url: String) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val bitmap = rememberRemoteImage(url)
    var fullscreen by remember { mutableStateOf(false) }

    if (fullscreen && bitmap != null) {
        // A Dialog, not a Box: this widget is laid out inside a bubble, so an
        // overlay drawn here would be clipped to the bubble's bounds.
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { fullscreen = false },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = alt.ifBlank { null },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close),
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(24.dp),
                    tint = Color.White,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(inkCodeBg(inkDark))
            .then(if (bitmap != null) Modifier.clickable { fullscreen = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = alt.ifBlank { null },
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
            else -> Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (alt.isNotBlank()) {
                    Text(
                        alt,
                        fontSize = 11.sp,
                        color = inkTextMuted(inkDark),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // No spinner: the image either arrives or it does not, and a
                // spinner in a transcript that scrolls is noise.
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp), tint = inkTextMuted(inkDark))
            }
        }
    }
}

/**
 * A message that is nothing but a URL becomes a card, not a wall of text.
 *
 * Long links in a chat bubble are unreadable and push the reply off screen; the
 * official client renders the same case as `.markdown-link-card`.
 */
@Composable
internal fun MarkdownLinkCard(url: String, onOpen: (String) -> Unit) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen(url) },
        color = inkCodeBg(inkDark),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, inkBorder(inkDark)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = inkTextMuted(inkDark))
            Column(Modifier.weight(1f)) {
                Text(
                    linkHost(url),
                    fontSize = 12.sp,
                    color = inkTextPrimary(inkDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    url,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = inkTextMuted(inkDark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** `https://example.com/a/b` → `example.com`; falls back to the whole string. */
internal fun linkHost(url: String): String =
    url.substringAfter("://", url).substringBefore('/').ifBlank { url }

