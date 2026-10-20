package com.laozhudu.ekkostudio

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.font.FontFamily

/**
 * Update centre: Studio itself, Hermes, and the coding agents.
 *
 * Versions come from the vendor's published catalog
 * (api.ekkostudio.xyz/api/studio/versions) rather than the preview git tags —
 * those belong to the source-preview flow and never matched an installed build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioUpdateScreen(
    versions: ServerVersions?,
    remote: RemoteVersions?,
    agents: List<AgentPolicy>,
    preview: StudioUpdatePreview?,
    tags: List<StudioUpdateTag>,
    loading: Boolean,
    hermesDeploy: String,
    hermesLog: String,
    hermesUpdating: Boolean,
    progress: Int,
    restartRequired: Boolean,
    restarting: Boolean,
    onRefresh: () -> Unit,
    onUpdateStudio: () -> Unit,
    onUpdateHermes: () -> Unit,
    onRestart: () -> Unit,
    onUpdateAgent: (String) -> Unit,
    onPreviewAction: (String, String?) -> Unit,
    onBack: () -> Unit,
) {
    var confirmStudio by remember { mutableStateOf(false) }
    var confirmAgent by remember { mutableStateOf<AgentPolicy?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var confirmHermes by remember { mutableStateOf(false) }

    val latestStudio = remote?.studio.orEmpty()
    val studioOutdated = latestStudio.isNotBlank() &&
        latestStudio != versions?.studio.orEmpty()
    val latestHermes = remote?.hermes?.maxByOrNull { versionWeight(it) }.orEmpty()
    val hermesOutdated = latestHermes.isNotBlank() &&
        versionWeight(latestHermes) > versionWeight(versions?.agent.orEmpty())

    confirmStudio.takeIf { it }?.let {
        AlertDialog(
            onDismissRequest = { confirmStudio = false },
            title = { Text(stringResource(R.string.action_update)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_update_studio,
                        latestStudio.ifBlank { "—" },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmStudio = false; onUpdateStudio() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStudio = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmHermes) {
        AlertDialog(
            onDismissRequest = { confirmHermes = false },
            title = { Text(stringResource(R.string.action_update)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_update_hermes,
                        hermesDeploy.ifBlank { "auto" },
                        latestHermes.ifBlank { "—" },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmHermes = false; onUpdateHermes() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmHermes = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    confirmAgent?.let { agent ->
        AlertDialog(
            onDismissRequest = { confirmAgent = null },
            title = { Text(stringResource(R.string.action_update)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_update_agent,
                        agent.id,
                        agent.latest.ifBlank { "—" },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmAgent = null; onUpdateAgent(agent.id) }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAgent = null }) {
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
        if (versions == null && loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hermesUpdating || (progress in 0..99)) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (progress in 0..100) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            if (progress in 0..100) "$progress%" else stringResource(R.string.update_in_progress),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (restartRequired) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.update_complete_restart),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.update_complete_restart_detail),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = onRestart,
                                enabled = !restarting,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(
                                    if (restarting) stringResource(R.string.server_restarting)
                                    else stringResource(R.string.action_restart_server),
                                )
                            }
                        }
                    }
                }
            }

            if (remote == null) {
                item {
                    Text(
                        stringResource(R.string.remote_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Studio", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${versions?.studio.orEmpty().ifBlank { "—" }} → " +
                                latestStudio.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (studioOutdated) {
                                    stringResource(R.string.available_new, latestStudio)
                                } else {
                                    stringResource(R.string.up_to_date)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (studioOutdated) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { confirmStudio = true },
                                enabled = studioOutdated && !loading,
                            ) { Text(stringResource(R.string.action_update)) }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Hermes", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${versions?.agent.orEmpty().ifBlank { "—" }} → " +
                                latestHermes.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            // The updater decides how to upgrade from this, not the app:
                            // git checkouts, npm globals and docker images each need a
                            // different command.
                            if (hermesDeploy.isBlank()) {
                                stringResource(R.string.hermes_deploy_unknown)
                            } else {
                                stringResource(R.string.hermes_deploy, hermesDeploy)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hermesOutdated) {
                                    stringResource(R.string.available_new, latestHermes)
                                } else {
                                    stringResource(R.string.up_to_date)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { confirmHermes = true },
                                enabled = !hermesUpdating && !restarting,
                            ) { Text(stringResource(R.string.action_update)) }
                        }
                        if (hermesLog.isNotBlank()) {
                            Text(
                                hermesLog.takeLast(4_000),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.section_agents),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            items(agents, key = { it.id }) { agent ->
                val installed = agent.current.isNotBlank()
                val outdated = agent.latest.isNotBlank() &&
                    agent.latest != agent.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agent.id, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (installed) {
                                "${agent.current} → ${agent.latest.ifBlank { "—" }}"
                            } else {
                                stringResource(R.string.agent_not_installed) +
                                    " · ${agent.latest.ifBlank { "—" }}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { confirmAgent = agent },
                        enabled = !loading && (outdated || !installed) &&
                            agent.latest.isNotBlank(),
                    ) {
                        Text(
                            stringResource(
                                if (installed) R.string.action_update else R.string.action_install,
                            ),
                        )
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                TextButton(onClick = { showPreview = !showPreview }) {
                    Text(stringResource(R.string.developer_preview))
                }
            }

            if (showPreview) {
                item {
                    Text(
                        stringResource(R.string.studio_update_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("prepare", "start", "stop", "install").forEach { action ->
                            TextButton(
                                onClick = { onPreviewAction(action, null) },
                                enabled = !loading,
                            ) { Text(action) }
                        }
                    }
                }
                item {
                    Text(
                        stringResource(
                            R.string.studio_update_current,
                            preview?.currentTag.orEmpty().ifBlank { "—" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(tags.take(15), key = { it.name }) { tag ->
                    Text(
                        tag.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { }.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** "0.22.2" -> 2202, so semantic versions compare numerically. */
private fun versionWeight(version: String): Int {
    val parts = version.removePrefix("v").split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return major * 10000 + minor * 100 + patch
}
