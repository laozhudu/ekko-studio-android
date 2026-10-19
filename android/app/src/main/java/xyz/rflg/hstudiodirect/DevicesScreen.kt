package xyz.rflg.hstudiodirect

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Official `pages/devices` (uni-app scope `data-v-61a2f361`).
 *
 * Every metric below is transcribed from
 * `docs/reference/apk-1.0.3/css/pages-devices.css`; the structural decisions
 * (what a card shows, which badges exist, what each panel does) come from the
 * page's compiled render function in the APK's `app-service.js`, which is the
 * only specification that exists for this screen — the uni-app source is closed.
 *
 * Two things the official page does are deliberately *not* reproduced, because
 * reproducing them would mean faking a cloud account this build does not have:
 * the 扫码登录官网 action and 删除账号 (both authenticate against the vendor's
 * cloud). Everything else, including the cloud option of the official
 * `ApiRouteSwitch`, is present and marked unavailable rather than removed, so
 * the shape of the official screen is still legible. See docs/parity/devices.md.
 */
@Composable
internal fun DevicesScreen(state: UiState, viewModel: AppViewModel) {
    val dark = MaterialTheme.colorScheme.isInkDark()
    val metrics = devicesMetrics()

    // Panels are page-local, exactly like the official page's refs: only one of
    // them is open at a time and none of them survives leaving the screen.
    var panel by remember { mutableStateOf(DevicePanel.None) }
    var renameTarget by remember { mutableStateOf<StudioInstance?>(null) }
    var accountMenu by remember { mutableStateOf(false) }

    // The official page renders the grid straight away when it already knows a
    // local device (`r.value = e.length === 0`), so only a cold list shows the
    // full-height loading state.
    val loading = state.busy && state.instances.isEmpty()
    val error = state.error

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            DevicesHeader(
                metrics = metrics,
                dark = dark,
                count = state.instances.size,
                avatarInitial = (state.currentUser?.username ?: state.account.orEmpty())
                    .firstOrNull()?.uppercase() ?: "S",
                menuOpen = accountMenu,
                onToggleMenu = { accountMenu = !accountMenu },
                onBack = { viewModel.back() },
            )

            Box(Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .widthIn(max = metrics.maxWidth)
                        .align(Alignment.TopCenter),
                ) {
                    if (loading) {
                        LoadingState()
                    } else {
                        DeviceGrid(
                            metrics = metrics,
                            dark = dark,
                            state = state,
                            onOpen = { viewModel.connectDevice(it.url) },
                            onRename = { renameTarget = it },
                            onDelete = { viewModel.removeInstance(it.url) },
                            onRetry = { viewModel.refreshDevices() },
                            onAdd = { viewModel.dismissError(); panel = DevicePanel.Methods },
                            // A page error while a panel is open belongs to that
                            // panel, which renders it as `.pairing-error`.
                            gridError = if (panel == DevicePanel.None) error else null,
                        )
                    }
                    Spacer(Modifier.height(metrics.contentBottom))
                }
            }
        }

        // Official `.account-popover` is page-level too: the backdrop is
        // `position:fixed` and the panel is absolute against
        // `.account-menu-anchor`, i.e. 43px below the trigger.
        if (accountMenu) {
            AccountPopover(
                dark = dark,
                metrics = metrics,
                name = state.currentUser?.username ?: state.account.orEmpty(),
                detail = hostLabel(state.baseUrl),
                initial = (state.currentUser?.username ?: state.account.orEmpty())
                    .firstOrNull()?.uppercase() ?: "S",
                topOffset = metrics.headerTop + 43.dp,
                onDismiss = { accountMenu = false },
                onAbout = { accountMenu = false; viewModel.openAboutFromDevices() },
                onLogout = { accountMenu = false; viewModel.signOut() },
                onRoute = {
                    accountMenu = false
                    viewModel.dismissError()
                    panel = DevicePanel.Route
                },
            )
        }

        when (panel) {
            DevicePanel.Methods -> PairingMethodsPanel(
                metrics = metrics,
                onDismiss = { panel = DevicePanel.None },
                onScan = { panel = DevicePanel.Scan },
                onManual = { panel = DevicePanel.Manual },
            )

            DevicePanel.Manual -> ManualPairingPanel(
                metrics = metrics,
                dark = dark,
                saving = state.devicesUi.saving,
                error = error,
                onDismiss = { panel = DevicePanel.None },
                onSubmit = { connection, name, user, password ->
                    viewModel.addDevice(connection, name, user, password)
                },
            )

            DevicePanel.Scan -> ScanPanel(
                metrics = metrics,
                onDismiss = { panel = DevicePanel.None },
                onPaste = { viewModel.dismissError(); panel = DevicePanel.Manual },
            )

            // `.device-route-editor` hosts the official route picker. Choosing the
            // cloud option is refused by the view model, and its message lands in
            // `state.error`, which is what this panel shows.
            DevicePanel.Route -> RoutePanel(
                metrics = metrics,
                selected = state.devicesUi.route,
                error = error,
                onSelect = { viewModel.setApiRoute(it) },
                onDismiss = { viewModel.dismissError(); panel = DevicePanel.None },
            )

            DevicePanel.None -> Unit
        }

        renameTarget?.let { target ->
            RenameDevicePanel(
                metrics = metrics,
                dark = dark,
                initial = target.label,
                saving = state.devicesUi.saving,
                onDismiss = { renameTarget = null },
                onSubmit = { name ->
                    viewModel.renameInstance(target.url, name)
                    renameTarget = null
                },
            )
        }
    }
}

/** The overlay the page is showing; the official page keeps four separate refs. */
private enum class DevicePanel { None, Methods, Manual, Scan, Route }

// ── responsive metrics ────────────────────────────────────────────────────
//
// The official page has three breakpoints, and they are *not* the login page's:
//
//   @media (orientation:landscape) and (min-width:900px) and (min-height:600px)
//   @media (orientation:landscape) and (max-height:599px)
//   @media (max-width:520px)
//
// The unqualified rules are the phone-portrait case.

private data class DevicesMetrics(
    val maxWidth: Dp,
    val horizontalPadding: Dp,
    val headerTop: Dp,
    val headerBottom: Dp,
    val contentBottom: Dp,
    val headingGap: Dp,
    val titleSize: TextUnit,
    val titleLine: TextUnit,
    val descriptionSize: TextUnit,
    val descriptionLine: TextUnit,
    val headerActionsGap: Dp,
    val triggerSize: Dp,
    val countHeight: Dp,
    val columns: Int,
    val gridGap: Dp,
    val cardPadding: Dp,
    val cardRadius: Dp,
    val cardMinHeight: Dp,
    val copyTop: Dp,
    val copyGap: Dp,
    val versionTop: Dp,
    val versionGap: Dp,
    val footerTop: Dp,
    val actionsTop: Dp,
    val actionsPadding: Dp,
    val actionsGap: Dp,
    val actionHeight: Dp,
    val actionFont: TextUnit,
)

@Composable
private fun devicesMetrics(): DevicesMetrics {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    // `.devices-header-content` is `calc(18px + var(--app-safe-area-top)) 18px 18px`,
    // so the status-bar inset is added rather than replaced.
    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // @media (orientation: landscape) and (min-width: 900px) and (min-height: 600px)
    if (landscape && configuration.screenWidthDp >= 900 && configuration.screenHeightDp >= 600) {
        return DevicesMetrics(
            maxWidth = 1240.dp,
            horizontalPadding = 18.dp,
            headerTop = safeTop + 18.dp,
            headerBottom = 18.dp,
            contentBottom = 28.dp,
            headingGap = 4.dp,
            titleSize = 22.sp,
            titleLine = 29.sp,
            descriptionSize = 12.sp,
            descriptionLine = 18.sp,
            headerActionsGap = 9.dp,
            triggerSize = 34.dp,
            countHeight = 24.dp,
            // grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)) — the
            // Compose grid is manual, so the column count is derived the same way.
            columns = ((configuration.screenWidthDp - 32) / 312).coerceAtLeast(2),
            gridGap = 12.dp,
            cardPadding = 17.dp,
            cardRadius = 17.dp,
            cardMinHeight = 170.dp,
            copyTop = 18.dp,
            copyGap = 4.dp,
            versionTop = 11.dp,
            versionGap = 6.dp,
            footerTop = 12.dp,
            actionsTop = 11.dp,
            actionsPadding = 10.dp,
            actionsGap = 7.dp,
            actionHeight = 28.dp,
            actionFont = 10.sp,
        )
    }

    // @media (orientation: landscape) and (max-height: 599px)
    if (landscape && configuration.screenHeightDp < 600) {
        return DevicesMetrics(
            maxWidth = Dp.Unspecified,
            horizontalPadding = 12.dp,
            headerTop = safeTop + 6.dp,
            headerBottom = 8.dp,
            contentBottom = 12.dp,
            headingGap = 1.dp,
            titleSize = 19.sp,
            titleLine = 24.sp,
            descriptionSize = 10.sp,
            descriptionLine = 14.sp,
            headerActionsGap = 7.dp,
            triggerSize = 30.dp,
            countHeight = 22.dp,
            columns = if (configuration.screenWidthDp <= 520) 1 else 2,
            gridGap = 10.dp,
            cardPadding = 12.dp,
            cardRadius = 14.dp,
            // min-height: 0 in this block; the add card just hugs its content.
            cardMinHeight = 0.dp,
            copyTop = 9.dp,
            copyGap = 2.dp,
            versionTop = 7.dp,
            versionGap = 4.dp,
            footerTop = 8.dp,
            actionsTop = 7.dp,
            actionsPadding = 7.dp,
            actionsGap = 5.dp,
            actionHeight = 26.dp,
            actionFont = 9.sp,
        )
    }

    val single = configuration.screenWidthDp <= 520
    return DevicesMetrics(
        maxWidth = 680.dp,
        horizontalPadding = 18.dp,
        headerTop = safeTop + 18.dp,
        headerBottom = 18.dp,
        contentBottom = 28.dp,
        headingGap = 4.dp,
        titleSize = 22.sp,
        titleLine = 29.sp,
        descriptionSize = 12.sp,
        descriptionLine = 18.sp,
        headerActionsGap = 9.dp,
        triggerSize = 34.dp,
        countHeight = 24.dp,
        columns = if (single) 1 else 2,
        gridGap = if (single) 12.dp else 12.dp,
        cardPadding = 17.dp,
        cardRadius = 17.dp,
        // @media (max-width: 520px) drops the card to 156px.
        cardMinHeight = if (single) 156.dp else 170.dp,
        copyTop = 18.dp,
        copyGap = 4.dp,
        versionTop = 11.dp,
        versionGap = 6.dp,
        footerTop = 12.dp,
        actionsTop = 11.dp,
        actionsPadding = 10.dp,
        actionsGap = 7.dp,
        actionHeight = 28.dp,
        actionFont = 10.sp,
    )
}

// ── `.devices-header` ─────────────────────────────────────────────────────

/**
 * `.devices-header` → `.devices-header-content` → `.section-heading`.
 *
 * The official header is *not* an app bar: it is a fixed block inside the page
 * that keeps the title, the device count and the account trigger on one row.
 */
@Composable
private fun DevicesHeader(
    metrics: DevicesMetrics,
    dark: Boolean,
    count: Int,
    avatarInitial: String,
    menuOpen: Boolean,
    onToggleMenu: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = metrics.maxWidth)
                .padding(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    top = metrics.headerTop,
                    bottom = metrics.headerBottom,
                ),
            // A back affordance the official page does not need: uni-app's page
            // stack owns it, whereas this screen is pushed inside one activity.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(metrics.headerActionsGap),
        ) {
            DevicesBackButton(metrics, onBack)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.headingGap)) {
                Text(
                    stringResource(R.string.devices_title),
                    fontSize = metrics.titleSize,
                    lineHeight = metrics.titleLine,
                    fontWeight = FontWeight.W600,
                    letterSpacing = (-0.25).sp,
                    color = inkTextPrimary(dark),
                )
                Text(
                    stringResource(R.string.devices_description),
                    fontSize = metrics.descriptionSize,
                    lineHeight = metrics.descriptionLine,
                    color = inkTextSecondary(dark),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.headerActionsGap),
            ) {
                if (count > 0) DeviceCount(count, metrics, dark)
                AccountTrigger(metrics, dark, avatarInitial, onToggleMenu)
            }
        }
    }
}

@Composable
private fun DevicesBackButton(metrics: DevicesMetrics, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(metrics.triggerSize)
            .clip(CircleShape)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `.device-count` — `min-width:24px; height:24px; padding:0 7px; radius 12px`. */
@Composable
private fun DeviceCount(count: Int, metrics: DevicesMetrics, dark: Boolean) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 24.dp, minHeight = metrics.countHeight)
            .height(metrics.countHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(inkSecondaryBg(dark))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            fontSize = 11.sp,
            color = inkTextSecondary(dark),
        )
    }
}

/** `.account-trigger` — a 34dp circular avatar button. */
@Composable
private fun AccountTrigger(metrics: DevicesMetrics, dark: Boolean, initial: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(metrics.triggerSize)
            .clip(CircleShape)
            .background(inkCardBg(dark))
            .border(1.dp, inkBorder(dark), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = inkTextPrimary(dark),
        )
    }
}

/**
 * `.account-popover` — 260dp, top 43px, right 0, radius 13px.
 *
 * The 账号权益 block renders the official `.account-entitlements-unavailable`
 * state ("权益状态暂不可用") instead of the two entitlement rows: those rows are
 * cloud subscriptions, and this build has no cloud account to report. The
 * official stylesheet already defines that branch, so this is the page's own way
 * of saying "no entitlement data", not an invention.
 */
@Composable
private fun AccountPopover(
    dark: Boolean,
    metrics: DevicesMetrics,
    name: String,
    detail: String,
    initial: String,
    topOffset: Dp,
    onDismiss: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onRoute: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // `.account-popover-backdrop { position:fixed; background:transparent }`
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = topOffset, end = metrics.horizontalPadding),
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(inkCardBg(dark))
                    .border(1.dp, inkBorder(dark), RoundedCornerShape(13.dp))
                    .padding(8.dp),
            ) {
                // .account-summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(inkSecondaryBg(dark)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initial, fontSize = 12.sp, fontWeight = FontWeight.W600, color = inkTextPrimary(dark))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            name.ifBlank { detail },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.W600,
                            color = inkTextPrimary(dark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (detail.isNotBlank()) {
                            Text(
                                detail,
                                fontSize = 9.sp,
                                lineHeight = 14.sp,
                                color = inkTextMuted(dark),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // .account-entitlements
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 10.dp, bottom = 10.dp),
                ) {
                    Text(
                        stringResource(R.string.devices_account_entitlements),
                        fontSize = 9.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.W600,
                        color = inkTextMuted(dark),
                    )
                    Text(
                        stringResource(R.string.devices_account_entitlements_unavailable),
                        fontSize = 8.sp,
                        lineHeight = 13.sp,
                        color = inkTextMuted(dark),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                AccountAction(
                    icon = Icons.Filled.Settings,
                    label = stringResource(R.string.devices_route_title),
                    detail = stringResource(R.string.devices_route_lan),
                    dark = dark,
                    onClick = onRoute,
                )
                AccountAction(
                    icon = Icons.Filled.Info,
                    label = stringResource(R.string.devices_account_about),
                    dark = dark,
                    onClick = onAbout,
                )
                AccountAction(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = stringResource(R.string.devices_account_logout),
                    dark = dark,
                    onClick = onLogout,
                )
            }
        }
    }
}

/** `.account-about` / `.account-logout` — 36dp rows with a 24dp icon slot. */
@Composable
private fun AccountAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String? = null,
    danger: Boolean = false,
    dark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (danger) inkError(dark) else inkTextSecondary(dark),
            )
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = if (danger) inkError(dark) else inkTextSecondary(dark),
            textAlign = TextAlign.Start,
        )
        if (detail != null) {
            Text(detail, fontSize = 9.sp, color = inkTextMuted(dark))
        }
    }
}

// ── `.device-grid` ────────────────────────────────────────────────────────

@Composable
private fun DeviceGrid(
    metrics: DevicesMetrics,
    dark: Boolean,
    state: UiState,
    onOpen: (StudioInstance) -> Unit,
    onRename: (StudioInstance) -> Unit,
    onDelete: (StudioInstance) -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    gridError: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.gridGap),
    ) {
        // `.device-grid > .error-state` spans the whole grid (`grid-column:1/-1`)
        // and is a row, not the full-height empty state.
        gridError?.let {
            ErrorBanner(message = it, dark = dark, onRetry = onRetry)
        }

        val entries = state.instances
        val rows = entries.chunked(metrics.columns)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(metrics.gridGap),
            ) {
                row.forEach { item ->
                    DeviceCard(
                        metrics = metrics,
                        dark = dark,
                        item = item,
                        probe = state.devicesUi.probe(item.url),
                        checking = state.devicesUi.isChecking(item.url),
                        connecting = state.devicesUi.connecting == item.url,
                        deleting = state.devicesUi.deleting == item.url,
                        active = item.url.trimEnd('/') == state.baseUrl.trimEnd('/'),
                        onOpen = { onOpen(item) },
                        onRename = { onRename(item) },
                        onDelete = { onDelete(item) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // Keeps the last card of an odd row at half width, the way a grid
                // does not stretch a lone item across both tracks.
                repeat(metrics.columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        AddDeviceCard(metrics = metrics, dark = dark, onClick = onAdd)
    }
}

/**
 * `.device-card` — 170dp minimum, 17dp radius, 17dp padding.
 *
 * The card is the whole tap target (official `onClick: he(device)`), so the
 * action row stops propagation — in Compose that is simply a separate clickable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    metrics: DevicesMetrics,
    dark: Boolean,
    item: StudioInstance,
    probe: DeviceProbe?,
    checking: Boolean,
    connecting: Boolean,
    deleting: Boolean,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = probe?.online == true
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = metrics.cardMinHeight)
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(inkCardBg(dark))
            .border(1.dp, inkBorderLight(dark), RoundedCornerShape(metrics.cardRadius))
            // `.device-card--connecting { pointer-events:none; opacity:.72 }`
            .alpha(if (connecting) 0.72f else 1f)
            .clickable(enabled = !connecting, onClick = onOpen)
            .padding(metrics.cardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DeviceSymbol(dark)
            DeviceStatusPill(
                dark = dark,
                label = stringResource(
                    when {
                        connecting -> R.string.devices_status_connecting
                        online -> R.string.devices_status_online
                        checking -> R.string.devices_status_checking
                        else -> R.string.devices_status_offline
                    },
                ),
                online = online,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = metrics.copyTop),
            verticalArrangement = Arrangement.spacedBy(metrics.copyGap),
        ) {
            // `.device-name` is clamped to one line, and the official helper caps
            // it at 40 characters before the ellipsis.
            Text(
                deviceDisplayName(item.label),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.W600,
                color = inkTextPrimary(dark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                deviceSystemLine(item, probe, active),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = inkTextSecondary(dark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // .device-version-row — a single wrapping row of chips, in the official
        // order: source tag, route tag, endpoint tag, "Studio <version>".
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = metrics.versionTop),
            horizontalArrangement = Arrangement.spacedBy(metrics.versionGap),
            verticalArrangement = Arrangement.spacedBy(metrics.versionGap),
        ) {
            // Every device in this app is reached directly, so the official
            // `--local` badge is the only source value that can appear here. The
            // `--cloud` variant exists in DeviceTag and is pinned by the parity
            // test, ready for a route that is not the vendor's cloud.
            DeviceTag(
                label = stringResource(R.string.devices_source_local),
                tone = DeviceTagTone.Local,
                dark = dark,
            )
            // Official `device-route-tag`: which route is carrying this device.
            // Upstream prints it only for cloud devices; a direct connection is
            // equally a route, and the matrix lists `.device-route-tag` as the
            // active-route badge, so it is shown for the LAN route here.
            DeviceTag(
                label = stringResource(R.string.devices_route_lan),
                tone = DeviceTagTone.Route,
                dark = dark,
                monospace = false,
            )
            deviceEndpointKind(item.endpointKind).takeIf { it.isNotEmpty() }?.let { kind ->
                DeviceTag(
                    label = stringResource(
                        if (kind == "desktop") R.string.devices_endpoint_desktop else R.string.devices_endpoint_web,
                    ),
                    tone = if (kind == "desktop") DeviceTagTone.Desktop else DeviceTagTone.Web,
                    dark = dark,
                )
            }
            DeviceTag(
                label = stringResource(
                    R.string.devices_studio_version,
                    probe?.webUiVersion?.takeIf { it.isNotBlank() }
                        ?: item.webUiVersion.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.devices_version_unknown),
                ),
                tone = DeviceTagTone.Plain,
                dark = dark,
            )
        }

        Spacer(Modifier.weight(1f))
        val lastSeen = maxOf(item.lastSeenAt, probe?.checkedAt ?: 0L)
        val age = deviceAge(online, lastSeen, System.currentTimeMillis() / 1000)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = metrics.footerTop),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                when (age) {
                    DeviceAge.Online -> stringResource(R.string.devices_footer_online)
                    DeviceAge.Never -> stringResource(R.string.devices_footer_never)
                    DeviceAge.JustNow -> stringResource(R.string.devices_footer_just_now)
                    DeviceAge.Minutes -> stringResource(
                        R.string.devices_footer_minutes,
                        deviceAgeCount(age, lastSeen, System.currentTimeMillis() / 1000),
                    )
                    DeviceAge.Hours -> stringResource(
                        R.string.devices_footer_hours,
                        deviceAgeCount(age, lastSeen, System.currentTimeMillis() / 1000),
                    )
                    DeviceAge.Days -> stringResource(
                        R.string.devices_footer_days,
                        deviceAgeCount(age, lastSeen, System.currentTimeMillis() / 1000),
                    )
                },
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = inkTextMuted(dark),
                maxLines = 1,
            )
            // `.machine-id` prints `connectionUrl || machineId`; a direct device's
            // connection URL *is* the machine address.
            Text(
                item.host,
                modifier = Modifier.weight(1f, fill = false),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = inkTextMuted(dark),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // .device-card-actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = metrics.actionsTop + metrics.actionsPadding),
            horizontalArrangement = Arrangement.spacedBy(metrics.actionsGap),
        ) {
            DeviceCardAction(
                label = stringResource(R.string.devices_action_rename),
                metrics = metrics,
                dark = dark,
                onClick = onRename,
            )
            DeviceCardAction(
                label = stringResource(
                    if (deleting) R.string.devices_action_deleting else R.string.devices_action_delete,
                ),
                metrics = metrics,
                dark = dark,
                danger = true,
                enabled = !deleting,
                onClick = onDelete,
            )
        }
    }
}

/** `.device-symbol` — a 31×22 screen, a 2px LED, and a stand. */
@Composable
private fun DeviceSymbol(dark: Boolean) {
    val stroke = inkTextPrimary(dark)
    Box(Modifier.size(31.dp)) {
        Box(
            Modifier
                .size(width = 31.dp, height = 22.dp)
                .border(1.5.dp, stroke, RoundedCornerShape(5.dp)),
        ) {
            // .device-screen-dot: right 3px, bottom 3px, 2×2.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 3.dp, bottom = 3.dp)
                    .size(2.dp)
                    .background(stroke, CircleShape),
            )
        }
        // .device-stand: bottom 2px, left 10px, 11×6, bottom border 1.5px, with a
        // 1.5×5 stem that starts 1px above the box.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-2).dp)
                .size(width = 11.dp, height = 6.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(width = 11.dp, height = 1.5.dp)
                    .background(stroke),
            )
            Box(
                Modifier
                    .offset(x = 5.dp, y = (-1).dp)
                    .size(width = 1.5.dp, height = 5.dp)
                    .background(stroke),
            )
        }
    }
}

/** `.device-status` — the pill with a 5dp `.status-dot`. */
@Composable
private fun DeviceStatusPill(dark: Boolean, label: String, online: Boolean) {
    val content = if (online) if (dark) Color(0xFF8FC9A5) else Color(0xFF397454) else inkTextMuted(dark)
    val background = when {
        online && dark -> Color(0x1A8FC9A5)
        online -> Color(0x1A397454)
        else -> inkSecondaryBg(dark)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(content, CircleShape))
        Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = content)
    }
}

private enum class DeviceTagTone { Plain, Local, Cloud, Desktop, Web, Route }

/**
 * One chip in `.device-version-row`.
 *
 * The palette is the official one and differs per theme: local/desktop are the
 * same green in light mode but only the tag colours invert under `.theme-dark`.
 */
@Composable
private fun DeviceTag(
    label: String,
    tone: DeviceTagTone,
    dark: Boolean,
    monospace: Boolean = true,
) {
    val foreground: Color = when (tone) {
        DeviceTagTone.Local -> if (dark) Color(0xFF8FC9A5) else Color(0xFF397454)
        DeviceTagTone.Cloud -> if (dark) Color(0xFFBEA7E3) else Color(0xFF7657A6)
        DeviceTagTone.Desktop -> if (dark) Color(0xFF8FC9A5) else Color(0xFF397454)
        DeviceTagTone.Web -> if (dark) Color(0xFF99BDE6) else Color(0xFF3F648F)
        DeviceTagTone.Route -> inkTextPrimary(dark)
        DeviceTagTone.Plain -> inkTextSecondary(dark)
    }
    val background: Color = when (tone) {
        DeviceTagTone.Local -> if (dark) Color(0x1A8FC9A5) else Color(0x1A397454)
        DeviceTagTone.Cloud -> if (dark) Color(0x1ABEA7E3) else Color(0x1A7657A6)
        DeviceTagTone.Desktop -> if (dark) Color(0x1A8FC9A5) else Color(0x1A397454)
        DeviceTagTone.Web -> if (dark) Color(0x1A99BDE6) else Color(0x1A3F648F)
        else -> inkSecondaryBg(dark)
    }
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        fontSize = 8.sp,
        lineHeight = 12.sp,
        fontWeight = if (tone == DeviceTagTone.Plain) FontWeight.Normal else FontWeight.W600,
        color = foreground,
        maxLines = 1,
    )
}

/** `.device-card-action` — 28dp tall, evenly split across the row. */
@Composable
private fun RowScope.DeviceCardAction(
    label: String,
    metrics: DevicesMetrics,
    dark: Boolean,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(metrics.actionHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(inkSecondaryBg(dark))
            .alpha(if (enabled) 1f else 0.58f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = metrics.actionFont,
            fontWeight = FontWeight.W500,
            color = if (danger) inkError(dark) else inkTextSecondary(dark),
            maxLines = 1,
        )
    }
}

/** `.add-device-card` — a dashed tile that opens the pairing overlay. */
@Composable
private fun AddDeviceCard(metrics: DevicesMetrics, dark: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = metrics.cardMinHeight)
            .clip(RoundedCornerShape(metrics.cardRadius))
            .border(1.5.dp, inkBorder(dark), RoundedCornerShape(metrics.cardRadius))
            .clickable(onClick = onClick)
            .padding(metrics.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(inkSecondaryBg(dark)),
            contentAlignment = Alignment.Center,
        ) {
            Text("＋", fontSize = 24.sp, fontWeight = FontWeight.Light, color = inkTextPrimary(dark))
        }
        Spacer(Modifier.height(3.dp + 6.dp))
        Text(
            stringResource(R.string.devices_add_title),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.W600,
            color = inkTextPrimary(dark),
        )
        Text(
            stringResource(R.string.devices_add_description),
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = inkTextMuted(dark),
            textAlign = TextAlign.Center,
        )
    }
}

// ── page states ───────────────────────────────────────────────────────────

/** `.loading-state` — a spinner and 正在读取设备, min-height 170dp. */
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 170.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spinner(size = 18.dp, stroke = 1.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.devices_loading),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * `.device-grid > .error-state` — the in-grid variant: `grid-column:1/-1`,
 * `min-height:auto`, a row with the message and the `.retry-action`.
 */
@Composable
private fun ErrorBanner(message: String, dark: Boolean, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(inkSecondaryBg(dark))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = inkTextMuted(dark),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.devices_retry),
            modifier = Modifier.clickable(onClick = onRetry).padding(start = 12.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = inkTextPrimary(dark),
        )
    }
}

/**
 * `.loading-spinner` / `.button-spinner`.
 *
 * The official spinner is `border:1.5px solid currentColor; border-top-color:
 * transparent` spun by a CSS keyframe, i.e. a three-quarter ring. Compose has no
 * border-side colour, so the ring is drawn as the same arc and rotated on the
 * same 0.75s linear cycle.
 */
@Composable
private fun Spinner(size: Dp, stroke: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "device-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "device-spinner-angle",
    )
    Canvas(modifier = Modifier.size(size).rotate(angle)) {
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = stroke.toPx()),
        )
    }
}

// ── `.pairing-overlay` panels ─────────────────────────────────────────────

/** `.pairing-overlay` + `.pairing-panel` scaffolding shared by every panel. */
@Composable
private fun PairingPanel(
    metrics: DevicesMetrics,
    dark: Boolean,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x6B000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding()
                .clip(RoundedCornerShape(20.dp))
                .background(inkCardBg(dark))
                .border(1.dp, inkBorderLight(dark), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        title,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.W600,
                        color = inkTextPrimary(dark),
                    )
                    Text(
                        description,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = inkTextSecondary(dark),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(inkSecondaryBg(dark))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", fontSize = 19.sp, fontWeight = FontWeight.Light, color = inkTextSecondary(dark))
                }
            }
            content()
        }
    }
}

/** `.method-list` — the official 添加设备 chooser. */
@Composable
private fun PairingMethodsPanel(
    metrics: DevicesMetrics,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onManual: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.isInkDark()
    PairingPanel(
        metrics = metrics,
        dark = dark,
        title = stringResource(R.string.devices_add_title),
        description = stringResource(R.string.devices_pairing_description),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MethodOption(
                glyph = "▣",
                title = stringResource(R.string.devices_method_scan),
                description = stringResource(R.string.devices_method_scan_description),
                dark = dark,
                onClick = onScan,
            )
            MethodOption(
                glyph = "⌨",
                title = stringResource(R.string.devices_method_manual),
                description = stringResource(R.string.devices_method_manual_description),
                dark = dark,
                onClick = onManual,
            )
        }
    }
}

/** `.method-option` — 68dp minimum, 14dp radius, 38dp icon tile. */
@Composable
private fun MethodOption(
    glyph: String,
    title: String,
    description: String,
    dark: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(inkInputBg(dark))
            .border(1.dp, inkBorderLight(dark), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(inkSecondaryBg(dark)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, fontSize = 20.sp, color = inkTextPrimary(dark))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.W600, color = inkTextPrimary(dark))
            Text(description, fontSize = 10.sp, lineHeight = 15.sp, color = inkTextMuted(dark))
        }
        Text("›", fontSize = 22.sp, fontWeight = FontWeight.Light, color = inkTextMuted(dark))
    }
}

/**
 * `.pairing-form` for 手动添加: 设备连接 / 设备名称 / Studio 账号 / Studio 密码.
 *
 * The four fields, their labels and their placeholders are the official ones; the
 * submit runs the app's existing sign-in, so this panel adds a device without
 * introducing a second credential path.
 */
@Composable
private fun ManualPairingPanel(
    metrics: DevicesMetrics,
    dark: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (connection: String, name: String, username: String, password: String) -> Unit,
) {
    var connection by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // Never rememberSaveable: a password must not reach the system saved-state bundle.
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    // Resolved here rather than inside the click lambda, which is not composable.
    val connectionRequired = stringResource(R.string.devices_error_connection)

    PairingPanel(
        metrics = metrics,
        dark = dark,
        title = stringResource(R.string.devices_add_title),
        description = stringResource(R.string.devices_manual_description),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PairingField(
                label = stringResource(R.string.devices_field_connection),
                value = connection,
                onValueChange = { connection = it; localError = null },
                placeholder = stringResource(R.string.devices_field_connection_hint),
                dark = dark,
                enabled = !saving,
                monospace = true,
                keyboardType = KeyboardType.Uri,
            )
            PairingField(
                label = stringResource(R.string.devices_field_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.devices_field_name_hint),
                dark = dark,
                enabled = !saving,
            )
            PairingField(
                label = stringResource(R.string.devices_field_account),
                value = username,
                onValueChange = { username = it },
                placeholder = stringResource(R.string.devices_field_account_hint),
                dark = dark,
                enabled = !saving,
            )
            PairingField(
                label = stringResource(R.string.devices_field_password),
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.devices_field_password_hint),
                dark = dark,
                enabled = !saving,
                visualTransformation = PasswordVisualTransformation(),
                imeAction = ImeAction.Done,
                onDone = { focus.clearFocus(); onSubmit(connection, name, username, password) },
            )

            (localError ?: error)?.let {
                Text(it, fontSize = 10.sp, lineHeight = 14.sp, color = inkError(dark))
            }

            // `.pairing-submit[disabled] { opacity:.58 }` with a 14dp button spinner.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(inkAccent(dark))
                    .alpha(if (saving) 0.58f else 1f)
                    .clickable(enabled = !saving) {
                        if (connection.isBlank()) {
                            localError = connectionRequired
                        } else {
                            focus.clearFocus()
                            onSubmit(connection, name, username, password)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (saving) {
                    Spinner(size = 14.dp, stroke = 1.5.dp, color = inkOnAccent(dark))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (saving) R.string.devices_pairing_submitting else R.string.devices_pairing_submit,
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = inkOnAccent(dark),
                )
            }
        }
    }
}

/**
 * The official 扫码添加 step.
 *
 * This build has no scanner wired up, so the option exists (the official method
 * list is part of the page's shape) but says so instead of failing silently —
 * pasting the same payload into the manual field is the working path, and the
 * panel hands the user straight to it.
 */
@Composable
private fun ScanPanel(metrics: DevicesMetrics, onDismiss: () -> Unit, onPaste: () -> Unit) {
    val dark = MaterialTheme.colorScheme.isInkDark()
    PairingPanel(
        metrics = metrics,
        dark = dark,
        title = stringResource(R.string.devices_method_scan),
        description = stringResource(R.string.devices_scan_description),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.devices_scan_unavailable),
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = inkTextSecondary(dark),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(inkAccent(dark))
                    .clickable(onClick = onPaste),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.devices_scan_paste_instead),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = inkOnAccent(dark),
                )
            }
        }
    }
}

/** `.pairing-field` + `.pairing-input`. */
@Composable
private fun PairingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    dark: Boolean,
    enabled: Boolean,
    monospace: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.W500,
            color = inkTextSecondary(dark),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(inkInputBg(dark))
                .border(
                    if (focused) 1.5.dp else 1.dp,
                    if (focused) inkAccent(dark) else inkInputBorder(dark),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = if (monospace) 12.sp else 13.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    color = inkTextPrimary(dark),
                ),
                cursorBrush = SolidColor(inkAccent(dark)),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = 13.sp,
                    color = inkTextMuted(dark),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The official 修改设备名称 overlay (`.pairing-panel`, one field). */
@Composable
private fun RenameDevicePanel(
    metrics: DevicesMetrics,
    dark: Boolean,
    initial: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    PairingPanel(
        metrics = metrics,
        dark = dark,
        title = stringResource(R.string.devices_rename_title),
        description = stringResource(R.string.devices_rename_description),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PairingField(
                label = stringResource(R.string.devices_field_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.devices_field_name_hint),
                dark = dark,
                enabled = !saving,
                imeAction = ImeAction.Done,
                onDone = { onSubmit(name) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(inkAccent(dark))
                    .alpha(if (saving) 0.58f else 1f)
                    .clickable(enabled = !saving) { onSubmit(name) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (saving) {
                    Spinner(size = 14.dp, stroke = 1.5.dp, color = inkOnAccent(dark))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (saving) R.string.devices_rename_saving else R.string.devices_rename_submit,
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = inkOnAccent(dark),
                )
            }
        }
    }
}

/**
 * The official "App 云端线路" overlay, which hosts the `ApiRouteSwitch`.
 *
 * Upstream this switches between the vendor's two cloud relays. Here the LAN
 * option is the only real one and the cloud option is shown disabled with the
 * reason spelled out — the task this app exists for is direct connection, and a
 * picker that silently offered a dead cloud route would be worse than no picker.
 */
@Composable
private fun RoutePanel(
    metrics: DevicesMetrics,
    selected: String,
    error: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.isInkDark()
    PairingPanel(
        metrics = metrics,
        dark = dark,
        title = stringResource(R.string.devices_route_title),
        description = stringResource(R.string.devices_route_description),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            ApiRouteSwitch(
                selected = selected,
                dark = dark,
                options = listOf(
                    ApiRouteOption(ApiRoute.LAN, stringResource(R.string.devices_route_lan), enabled = true),
                    ApiRouteOption(ApiRoute.CLOUD, stringResource(R.string.devices_route_cloud), enabled = false),
                ),
                onSelect = onSelect,
            )
            Text(
                error ?: stringResource(R.string.devices_route_cloud_unavailable),
                modifier = Modifier.padding(top = 10.dp),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = if (error != null) inkError(dark) else inkTextMuted(dark),
            )
        }
    }
}

internal data class ApiRouteOption(val value: String, val label: String, val enabled: Boolean)

/**
 * `.api-route-switch` — the official route picker (scope `data-v-ac60d9fd`).
 *
 * The official component also has a `.api-route-compact` variant; both are
 * implemented here, and [compact] selects between them exactly as the official
 * `compact` prop does. The picker is the one official affordance for choosing a
 * route, so it is where this build states its position on the cloud.
 */
@Composable
internal fun ApiRouteSwitch(
    selected: String,
    dark: Boolean,
    options: List<ApiRouteOption>,
    onSelect: (String) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        // `.api-route-compact { padding:2px; radius:8px; gap:2px }`
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(inkSecondaryBg(dark))
                .border(1.dp, inkInputBorder(dark), RoundedCornerShape(8.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val active = option.value == selected
                Text(
                    option.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) inkCardBg(dark) else Color.Transparent)
                        .alpha(if (option.enabled) 1f else 0.62f)
                        .clickable(enabled = option.enabled) { onSelect(option.value) }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = if (active) inkTextPrimary(dark) else inkTextMuted(dark),
                    maxLines = 1,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(inkCardBg(dark))
            .border(1.dp, inkInputBorder(dark), RoundedCornerShape(12.dp))
            .padding(13.dp),
    ) {
        // `.api-route-heading`: title 13px/19px, description 9px/14px.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.devices_route_title),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.W600,
                color = inkTextPrimary(dark),
            )
            Text(
                stringResource(R.string.devices_route_switch_description),
                fontSize = 9.sp,
                lineHeight = 14.sp,
                color = inkTextMuted(dark),
            )
        }
        // `.api-route-options { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); padding:3px; gap:3px }`
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(inkSecondaryBg(dark))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { option ->
                val active = option.value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 38.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) inkCardBg(dark) else Color.Transparent)
                        .border(
                            1.dp,
                            if (active) inkBorderLight(dark) else Color.Transparent,
                            RoundedCornerShape(7.dp),
                        )
                        .alpha(if (option.enabled) 1f else 0.62f)
                        .clickable(enabled = option.enabled) { onSelect(option.value) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = if (active) inkTextPrimary(dark) else inkTextSecondary(dark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── small helpers ─────────────────────────────────────────────────────────

/**
 * Official `Xe(device)`: the `.device-system` line.
 *
 * The probe's platform is preferred, the stored one is the fallback, and a
 * Studio that reports neither gets the official product-name fallback rather
 * than a blank line.
 */
@Composable
private fun deviceSystemLine(item: StudioInstance, probe: DeviceProbe?, active: Boolean): String {
    val platform = probe?.platform?.takeIf { it.isNotBlank() } ?: item.platform
    val family = deviceSystemLabel(platform)
    return when {
        family.isNotEmpty() -> family
        active -> stringResource(R.string.devices_system_active)
        else -> stringResource(R.string.devices_system_fallback)
    }
}
