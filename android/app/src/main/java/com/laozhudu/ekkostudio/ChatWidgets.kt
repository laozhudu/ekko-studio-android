package com.laozhudu.ekkostudio

// Chat-surface widgets extracted from MainActivity.kt. Pure move: no behaviour
// changed, so the transcript renders exactly as before.

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Done
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.animation.core.LinearEasing


/** Header row that backfills history; also acts as the spinner while loading. */
@Composable
internal fun LoadOlderRow(loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            stringResource(if (loading) R.string.history_loading_older else R.string.history_load_older),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


/** Centred day pill, Telegram's date separator. */
@Composable
internal fun DaySeparator(raw: String?) {
    val key = dayKeyOf(raw)
    if (key.isBlank()) return
    val today = dayKeyOf(System.currentTimeMillis().toString())
    val label = when (key) {
        today -> stringResource(R.string.day_today)
        else -> key
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


/** Local yyyy-MM-dd, used to decide where a date separator belongs. */
internal fun dayKeyOf(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val millis = raw.toLongOrNull()
    if (millis != null) {
        val normalized = if (millis < 100_000_000_000L) millis * 1000 else millis
        return android.text.format.DateFormat.format("yyyy-MM-dd", normalized).toString()
    }
    return raw.take(10)
}


/**
 * Telegram's inline keyboard: full-width rows separated by hairlines, attached to
 * the message that asked. Used for approvals and for clarifications that offer a
 * fixed set of answers; free-text clarification still opens the dialog.
 */
@Composable
internal fun InlineActionKeyboard(action: PendingRunAction, onChoice: (String) -> Unit) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val choices = if (action.kind == RequiredAction.Approval) {
        action.options.filter { it != "deny" }
    } else {
        action.options
    }
    if (action.kind == RequiredAction.Clarification && choices.isEmpty()) return
    if (choices.isEmpty() && action.kind == RequiredAction.Approval) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(12.dp),
            color = inkMessageBg(inkDark),
            border = BorderStroke(1.dp, inkBorder(inkDark)),
        ) {
            Column {
                Text(
                    action.prompt.ifBlank {
                        stringResource(
                            if (action.kind == RequiredAction.Approval) R.string.run_requires_approval
                            else R.string.run_requires_clarification,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = inkTextPrimary(inkDark),
                )
                HorizontalDivider(color = inkBorder(inkDark), thickness = 0.5.dp)
                choices.forEach { choice ->
                    Text(
                        labelForChoice(choice),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoice(choice) }
                            .padding(vertical = 11.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HorizontalDivider(color = inkBorder(inkDark), thickness = 0.5.dp)
                }
                if (action.kind == RequiredAction.Approval) {
                    Text(
                        stringResource(R.string.action_reject),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoice(action.options.firstOrNull { it == "deny" } ?: "deny") }
                            .padding(vertical = 11.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}


@Composable
internal fun labelForChoice(choice: String): String = when (choice) {
    "session" -> stringResource(R.string.approval_session)
    "always" -> stringResource(R.string.approval_always)
    "once" -> stringResource(R.string.approval_once)
    "deny" -> stringResource(R.string.action_reject)
    else -> choice
}


/**
 * Telegram's swipe-to-reply: drag a message to the right and it quotes itself in
 * the composer. The gesture is deliberately generous (56dp) because the same
 * drag also scrolls sideways in tables.
 */
@Composable
internal fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(targetValue = offset, animationSpec = tween(120), label = "swipeReply")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offset >= thresholdPx) onReply()
                        offset = 0f
                    },
                    onDragCancel = { offset = 0f },
                ) { _, dragAmount ->
                    offset = (offset + dragAmount).coerceIn(-12f, thresholdPx * 1.6f)
                }
            },
    ) {
        if (animated > 8f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = stringResource(R.string.message_reply),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(18.dp)
                    .alpha((animated / thresholdPx).coerceIn(0f, 1f)),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Box(modifier = Modifier.offset { IntOffset(animated.toInt(), 0) }) { content() }
    }
}


/**
 * Pinned notice for pending approvals, visible on any screen.
 *
 * Deliberately not a dialog: an approval that steals focus mid-typing is worse
 * than a strip you can ignore until you choose to act.
 */
@Composable
internal fun ApprovalBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.approval_banner, count),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.approval_banner_open), style = MaterialTheme.typography.labelMedium)
        }
    }
}



/**
 * The web's "thinking" shimmer, ported to Compose.
 *
 * Official implementation (MessageList CSS, `.thinking-status-label`):
 *   background: linear-gradient(105deg,
 *       var(--text-secondary) 0% 39%, #fff 48% 52%, var(--text-secondary) 61% 100%)
 *   background-size: 300% 100%; background-clip: text; color: transparent;
 *   animation: thinking-label-shimmer 2.2s linear infinite  (100% -> 0%)
 *
 * In Compose the same effect is a text brush whose gradient slides across the
 * glyphs. Dark mode swaps the highlight for #2f3540, exactly like the web.
 */
@Composable
internal fun rememberShimmerBrush(dark: Boolean): androidx.compose.ui.graphics.Brush {
    val transition = rememberInfiniteTransition(label = "statusShimmer")
    val shift by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "statusShimmerShift",
    )
    val base = if (dark) Color(0xFFF0F0F0) else Color(0xFF666666)
    val highlight = if (dark) Color(0xFF2F3540) else Color(0xFFFFFFFF)
    // 300% of a nominal label width, so the bright band sweeps the whole line.
    val width = 900f
    val startX = shift * width
    return Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to base,
            0.39f to base,
            0.48f to highlight,
            0.52f to highlight,
            0.61f to base,
            1.00f to base,
        ),
        start = androidx.compose.ui.geometry.Offset(startX, 0f),
        end = androidx.compose.ui.geometry.Offset(startX + width, 0f),
    )
}

/** One-line label with the official shimmer sweep. */
@Composable
internal fun ShimmerText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style.copy(brush = rememberShimmerBrush(dark)),
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Global run status pill — shown on every screen while a run is in flight.
 *
 * Telegram answers "is it working?" in the chat header; a header is not always on
 * screen here (settings, terminal, workspace), so the answer is a pill that
 * follows the user. Tapping it jumps to the conversation that owns the run.
 */
@Composable
internal fun GlobalRunStatusStrip(
    state: UiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.runStartedAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsed = if (state.runStartedAtMillis > 0) {
        ((now - state.runStartedAtMillis).coerceAtLeast(0) / 1000.0)
    } else 0.0
    val tool = state.lines.lastOrNull()?.tools?.lastOrNull { it.status == ToolRunStatus.Running }
    val title = state.sessions.firstOrNull { it.id == state.runSessionId }?.title
    val notice = state.runNotices.lastOrNull()

    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Surface(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(999.dp),
        // The web shows the live status on the assistant bubble surface with a
        // hairline; a tinted pill would fight the shimmer it sits behind.
        color = inkMessageBg(inkDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, inkSystemBorder(inkDark)),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = inkTextSecondary(inkDark),
            )
            val label = buildString {
                append(stringResource(R.string.run_status_working))
                if (elapsed >= 1) append(" ").append("%.0fs".format(Locale.US, elapsed))
                (tool?.name ?: title)?.let { append(" · ").append(it) }
                notice?.let { append(" · ").append(it.take(28)) }
            }
            ShimmerText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                dark = inkDark,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
/**
 * Telegram-style pulsing indicator for "working" state.
 */
internal fun AnimatedIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "workingIndicator")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}
