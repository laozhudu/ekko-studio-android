package com.laozhudu.ekkostudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    onBack: () -> Unit,
) {
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
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(tag.name, style = MaterialTheme.typography.bodyMedium)
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
