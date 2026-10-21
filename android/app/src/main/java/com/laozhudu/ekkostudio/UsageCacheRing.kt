package com.laozhudu.ekkostudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `.usage-cache-summary`: a 100dp ring with an 8dp stroke, the percentage at 22sp
 * inside it and a 10sp caption under that, then a 25sp headline figure beside it.
 *
 * The ring is drawn with an arc rather than a progress bar because the official
 * client does — and because a hit rate is a proportion of a whole, which a bar
 * with no end reads poorly.
 */
@Composable
fun CacheHitRing(rate: Double?, cacheTokens: Long, inputTokens: Long) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val percent = ((rate ?: 0.0) * 100).coerceIn(0.0, 100.0)
    val track = inkBorder(inkDark)
    val fill = if (percent >= 60) inkSuccess(inkDark) else inkInfo(inkDark)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(100.dp)) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (percent > 0) {
                    drawArc(
                        color = fill,
                        startAngle = -90f,
                        sweepAngle = 360f * (percent / 100.0).toFloat(),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (rate == null) "—" else "${"%.0f".format(percent)}%",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inkTextPrimary(inkDark),
                )
                Text(
                    stringResource(R.string.usage_cache_hit),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = inkTextMuted(inkDark),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                compactNumber(cacheTokens),
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = inkTextPrimary(inkDark),
            )
            Text(
                stringResource(R.string.usage_cache_read),
                fontSize = 10.sp,
                color = inkTextMuted(inkDark),
            )
            Text(
                stringResource(R.string.usage_fresh_input, compactNumber(inputTokens)),
                fontSize = 10.sp,
                color = inkTextMuted(inkDark),
            )
        }
    }
}

/** A chip row for `filter_options.accounts`; hidden when the server has one account. */
@Composable
fun UsageAccountRow(
    accounts: List<UsageAccount>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (accounts.size <= 1) return
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountChip(
            label = stringResource(R.string.usage_all_accounts),
            selected = selected == null,
            onClick = { onSelect(null) },
            inkDark = inkDark,
        )
        accounts.take(5).forEach { account ->
            AccountChip(
                label = account.username,
                selected = selected == account.id,
                onClick = { onSelect(account.id) },
                inkDark = inkDark,
            )
        }
    }
}

@Composable
private fun AccountChip(label: String, selected: Boolean, onClick: () -> Unit, inkDark: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) inkSelectedBg(inkDark) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) inkTextPrimary(inkDark) else inkTextSecondary(inkDark),
        )
    }
}
