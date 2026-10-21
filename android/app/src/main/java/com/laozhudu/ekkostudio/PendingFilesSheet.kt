package com.laozhudu.ekkostudio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「待添加文件」 — files shared in from another app, waiting to be attached.
 *
 * Layout and copy follow the official client's `.inbox-*` block: a bottom drawer
 * capped at 78vh and 560dp wide, a 32×4 handle, a 17sp/650 title with a 13sp
 * count, one 12dp-radius row per file with an 18dp checkbox, and an inverted
 * "add to this chat" button at the bottom. Its copy is worth keeping close to the
 * original because it answers the question a user actually has: *did this delete
 * my file?* — 「仅移除 App 暂存的副本，不会删除原文件」.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingFilesSheet(
    files: List<PendingFile>,
    selected: Set<String>,
    busy: Boolean,
    canAttach: Boolean,
    onToggle: (PendingFile) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onRemove: (PendingFile) -> Unit,
    onAttach: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // `.inbox-handle`: 32×4, 9dp top margin.
            Box(
                modifier = Modifier
                    .padding(top = 9.dp, bottom = 2.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(inkTextMuted(inkDark)),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.inbox_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight(650),
                    color = inkTextPrimary(inkDark),
                )
                Text(
                    files.size.toString(),
                    modifier = Modifier.padding(start = 5.dp),
                    fontSize = 13.sp,
                    color = inkTextSecondary(inkDark),
                )
                Box(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(40.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(22.dp),
                        tint = inkTextSecondary(inkDark),
                    )
                }
            }
            Text(
                stringResource(R.string.inbox_help),
                modifier = Modifier.padding(horizontal = 20.dp),
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = inkTextSecondary(inkDark),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSelectAll, enabled = !busy) {
                    Text(
                        stringResource(
                            if (selected.size == files.size && files.isNotEmpty()) R.string.inbox_deselect_all
                            else R.string.inbox_select_all,
                        ),
                    )
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onClearAll, enabled = !busy) {
                    Text(stringResource(R.string.inbox_clear_all), color = inkTextSecondary(inkDark))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            ) {
                items(files, key = { it.file.absolutePath }) { pending ->
                    val checked = pending.file.absolutePath in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, inkBorder(inkDark), RoundedCornerShape(12.dp))
                            .background(inkCardBg(inkDark))
                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).clickable { onToggle(pending) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            // `.inbox-check`: 18dp, 6dp radius, inverts when selected.
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (checked) inkTextPrimary(inkDark) else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (checked) inkTextPrimary(inkDark) else inkTextMuted(inkDark),
                                        RoundedCornerShape(6.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = inkBgPrimary(inkDark),
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(width = 25.dp, height = 29.dp),
                                tint = inkTextSecondary(inkDark),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    pending.name,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = inkTextPrimary(inkDark),
                                )
                                Text(
                                    listOf(compactNumber(pending.size) + "B", pending.mime.take(28))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = inkTextSecondary(inkDark),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.size(40.dp).clickable(enabled = !busy) { onRemove(pending) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.inbox_remove),
                                modifier = Modifier.size(20.dp),
                                tint = inkTextSecondary(inkDark),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.dp, color = androidx.compose.ui.graphics.Color.Transparent)
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // `.inbox-add`: inverted, 46dp tall, 13dp radius, disabled until
                // something is selected and a chat is open.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (canAttach && selected.isNotEmpty() && !busy) inkTextPrimary(inkDark)
                            else inkTextMuted(inkDark),
                        )
                        .clickable(enabled = canAttach && selected.isNotEmpty() && !busy, onClick = onAttach)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = inkBgPrimary(inkDark),
                        )
                    }
                    Text(
                        stringResource(R.string.inbox_add_to_chat) +
                            if (selected.isNotEmpty() && !busy) " (${selected.size})" else "",
                        modifier = Modifier.padding(start = if (busy) 8.dp else 0.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = inkBgPrimary(inkDark),
                    )
                }
                if (!canAttach) {
                    Text(
                        stringResource(R.string.inbox_need_chat),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 12.sp,
                        color = inkTextSecondary(inkDark),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                } else {
                    Text(
                        stringResource(R.string.inbox_footer_help),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 11.sp,
                        color = inkTextMuted(inkDark),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
