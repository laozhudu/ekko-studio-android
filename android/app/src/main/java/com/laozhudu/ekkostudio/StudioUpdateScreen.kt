package com.laozhudu.ekkostudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Read-only view of Studio's own update machinery.
 *
 * Installing from the phone is deliberately not offered: the install/start/stop
 * endpoints restart the very server this app talks to, and a mis-tap would drop
 * the connection mid-upgrade. The web UI remains the place to run it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioUpdateScreen(
    preview: StudioUpdatePreview?,
    tags: List<StudioUpdateTag>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onAction: (String, String?) -> Unit,
    onBack: () -> Unit,
) {
    var selectedTag by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    // Default the selection to the newest non-moving tag so Prepare has a target.
    LaunchedEffect(tags) {
        if (selectedTag.isBlank()) {
            selectedTag = tags.firstOrNull { it.name != "main" }?.name
                ?: tags.firstOrNull()?.name.orEmpty()
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(stringResource(R.string.studio_update_confirm_title, action)) },
            text = {
                Text(
                    stringResource(
                        R.string.studio_update_confirm_body,
                        action,
                        selectedTag.ifBlank { "—" },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(action, selectedTag.ifBlank { null })
                        pendingAction = null
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.studio_update_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                preview == null && loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                preview == null -> Text(
                    text = stringResource(R.string.studio_update_unavailable),
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        Text(
                            stringResource(
                                R.string.studio_update_current,
                                preview.currentTag.ifBlank { "—" },
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    item {
                        Text(
                            "preview exists=${preview.exists} · installed=${preview.installed} · running=${preview.running}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (preview.frontendUrl.isNotBlank()) {
                        item {
                            Text(
                                preview.frontendUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(
                                onClick = { pendingAction = "prepare" },
                                enabled = !loading,
                            ) { Text(stringResource(R.string.studio_update_prepare)) }
                            TextButton(
                                onClick = { pendingAction = "start" },
                                enabled = !loading,
                            ) { Text(stringResource(R.string.studio_update_start)) }
                            TextButton(
                                onClick = { pendingAction = "stop" },
                                enabled = !loading,
                            ) { Text(stringResource(R.string.studio_update_stop)) }
                            TextButton(
                                onClick = { pendingAction = "install" },
                                enabled = !loading,
                            ) { Text(stringResource(R.string.studio_update_install)) }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.studio_update_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        Text(
                            stringResource(R.string.studio_update_tags),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(tags.take(30), key = { it.name }) { tag ->
                        val picked = tag.name == selectedTag
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTag = tag.name }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (picked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (tag.sha.isNotBlank()) {
                                Text(
                                    tag.sha.take(10),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
