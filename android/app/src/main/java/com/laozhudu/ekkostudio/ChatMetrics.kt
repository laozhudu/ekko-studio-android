package com.laozhudu.ekkostudio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The official Android client's chat metrics, as numbers.
 *
 * Extracted from the shipped page stylesheet (`pages/index/index.css` inside the
 * official 1.0.8 APK). Only measurements and colour values are recorded here —
 * no stylesheet text is copied, because this repository is public and a
 * stylesheet is copyrighted expression, whereas "the session title is 13px with
 * an 18px line height" is a fact.
 *
 * Where the official client and this app disagreed, the official value won.
 */
object ChatMetrics {
    // ── Session list ────────────────────────────────────────────────────────
    /** Row padding; the row itself carries no avatar, only a small agent logo. */
    val sessionRowPaddingH = 10.dp
    val sessionRowPaddingV = 8.dp
    val sessionRowGap = 2.dp
    val sessionRowRadius = 6.dp
    /** List side padding, so rows inset from the drawer edge. */
    val sessionListPaddingH = 6.dp
    val sessionListPaddingBottom = 12.dp
    val sessionAgentLogo = 16.dp
    val sessionAgentLogoRadius = 3.dp
    val sessionTitleSize = 13.sp
    val sessionTitleLineHeight = 18.sp
    val sessionTitleWeight = 550
    val sessionTimeSize = 11.sp
    val sessionTimeLineHeight = 16.sp
    val sessionPreviewSize = 10.sp
    val sessionPreviewLineHeight = 14.sp
    val sessionPreviewGap = 3.dp
    val sessionTitleRowGap = 10.dp
    val sessionTitleMainGap = 5.dp
    val sessionPin = 13.dp
    val sessionUnreadDot = 6.dp
    val sessionUnreadRing = 3.dp
    /** The running bar is 2px, inside the row, not under it. */
    val sessionRunningTrack = 2.dp
    val sessionRunningTrackGap = 2.dp
    val sessionGroupHeadingPaddingTop = 16.dp
    val sessionGroupHeadingPaddingBottom = 4.dp
    val sessionGroupHeadingSize = 10.sp
    val sessionGroupHeadingLineHeight = 14.sp
    val sessionGroupHeadingWeight = 600
    val sessionGroupHeadingSpacing = 0.5.sp
    val sessionLoadMoreHeight = 32.dp
    val sessionLoadMoreSize = 10.sp

    // ── Conversation header ─────────────────────────────────────────────────
    val headerHeight = 46.dp
    val headerPaddingH = 14.dp
    val headerPaddingV = 3.dp
    val headerTitleSize = 15.sp
    val headerTitleLineHeight = 22.sp
    val headerTitleWeight = 650
    val headerButton = 38.dp
    val headerButtonRadius = 11.dp

    // ── Messages ────────────────────────────────────────────────────────────
    /** Between two messages, not inside one. */
    val messageGap = 14.dp
    val authorRowMinHeight = 22.dp
    val authorRowGap = 4.dp
    val authorRowBottomGap = 4.dp
    val authorAvatar = 22.dp
    val authorNameSize = 12.sp
    val authorNameLineHeight = 16.sp
    val authorNameWeight = 500
    val profileTagMaxWidth = 120.dp
    val profileTagPaddingH = 6.dp
    val profileTagPaddingV = 1.dp
    val profileTagRadius = 5.dp
    val profileTagSize = 9.sp
    val bubblePaddingH = 14.dp
    val bubblePaddingV = 10.dp
    val bubbleRadius = 10.dp
    val bubbleCommandPaddingH = 10.dp
    val bubbleCommandPaddingV = 8.dp
    val metaRowMinHeight = 24.dp
    val metaRowGap = 6.dp
    val metaRowTopGap = 4.dp
    val metaRowPaddingH = 4.dp
    val metaButton = 24.dp
    val metaButtonRadius = 6.dp
    val metaTimeSize = 11.sp
    val metaTimeLineHeight = 16.sp
    val listBottomGap = 12.dp
    val emptyStateMinHeight = 86.dp
    val emptyLogo = 48.dp
    val emptyLogoRadius = 12.dp

    // ── Process / thinking strip ────────────────────────────────────────────
    /** Fixed, so the composer never jumps while tools come and go. */
    val thinkingStripHeight = 32.dp
    val thinkingCopyHeight = 20.dp
    val thinkingTimeMinWidth = 30.dp
    val thinkingTimeSize = 12.sp
    val thinkingTimeLineHeight = 20.sp
    val toolPanelHeight = 26.dp
    val toolChipHeight = 26.dp
    val toolChipPaddingH = 8.dp
    val toolChipPaddingV = 3.dp
    val toolChipRadius = 7.dp
    val toolChipGap = 4.dp
    val toolChipMaxWidth = 260.dp
    val toolIcon = 12.dp
    val toolNameSize = 11.sp
    val toolDurationSize = 9.sp
    val toolBadgeSize = 9.sp
    val toolBadgeLineHeight = 14.sp
    val toolRunCardMaxWidth = 520.dp
    val toolRunHeaderMinHeight = 30.dp
    val toolRunHeaderPaddingH = 8.dp
    val toolRunHeaderPaddingV = 4.dp
    val toolRunHeaderGap = 7.dp
    val toolRunHeaderRadius = 8.dp
    val toolRunHeaderSize = 11.sp
    val toolRunItemsIndent = 11.dp
    val toolRunItemsPaddingLeft = 12.dp
    val toolLinePaddingH = 4.dp
    val toolLinePaddingV = 2.dp

    // ── Composer ────────────────────────────────────────────────────────────
    val composerPaddingH = 20.dp
    val composerPaddingTop = 8.dp
    val contextLabelMaxWidth = 112.dp
    val contextLabelSize = 9.sp
    val contextLabelLineHeight = 14.sp
    val contextMeterWidth = 34.dp
    val contextMeterHeight = 3.dp
    /** The official meter changes colour at 60% and 80%, and so does the label. */
    val contextWarningPercent = 60f
    val contextDangerPercent = 80f
}

/** `#d59a2d` — the official warning tone for the context meter, both themes. */
const val CONTEXT_WARNING_HEX = 0xFFD59A2D

/** Tool status colours: the official client uses these instead of the ink tokens. */
const val TOOL_SUCCESS_HEX = 0xFF52C41A
const val TOOL_ERROR_HEX = 0xFFFF4D4F
