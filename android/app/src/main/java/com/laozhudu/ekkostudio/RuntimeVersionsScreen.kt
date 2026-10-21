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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Hermes runtime and Web UI versions, plus the download jobs that move them.
 *
 * The server activates a runtime the moment its download finishes, so the
 * download button always passes through a warning: on this machine Hermes runs
 * from a git checkout and switching to the managed runtime is a real change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeVersionsScreen(state: UiState, vm: AppViewModel) {
    val versions = state.runtimeVersions
    var pendingDownload by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var confirmingRestart by remember { mutableStateOf(false) }
    val anyLive = state.runtimeJobs.any { it.isLive }
    val cliInstalls = versions?.cliInstallations.orEmpty()

    // Only poll while something is actually moving; an idle server costs nothing.
    LaunchedEffect(anyLive) {
        while (anyLive) {
            delay(2_000)
            vm.refreshRuntimeJobs()
        }
    }

    pendingDownload?.let { (version, webUi) ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text(stringResource(R.string.runtime_download_warning)) },
            text = { Text(version, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = { vm.downloadVersion(version, webUi); pendingDownload = null }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pendingDelete?.let { (version, webUi) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.runtime_confirm_delete)) },
            text = { Text(version, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                TextButton(onClick = { vm.deleteRuntimeVersion(version, webUi); pendingDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (confirmingRestart) {
        AlertDialog(
            onDismissRequest = { confirmingRestart = false },
            title = { Text(stringResource(R.string.restart_webui)) },
            text = { Text(stringResource(R.string.restart_webui_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.restartWebUi(); confirmingRestart = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRestart = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.runtime_versions_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::openRuntimeVersions) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "${versions?.platform.orEmpty()} · ${versions?.source.orEmpty()} ${versions?.agentVersion.orEmpty()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Running versions first: these are what the phone actually talks to.
            item { SectionLabel(stringResource(R.string.runtime_active)) }
            item {
                VersionRow(
                    label = "Runtime",
                    value = versions?.activeRuntime?.ifBlank { null } ?: stringResource(R.string.apikeys_never_used),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        VersionRow(label = "Web UI", value = versions?.activeWebUi.orEmpty())
                    }
                    TextButton(onClick = { confirmingRestart = true }) {
                        Text(stringResource(R.string.restart_webui))
                    }
                }
            }

            if (cliInstalls.isNotEmpty()) {
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                item { SectionLabel(stringResource(R.string.runtime_cli_installs)) }
                items(cliInstalls, key = { it.second }) { (version, path) ->
                    Text(
                        "$version  $path",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionLabel(stringResource(R.string.runtime_jobs)) }
            if (state.runtimeJobs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.runtime_no_jobs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.runtimeJobs, key = { it.id }) { job -> JobCard(job) }

            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item { SectionLabel(stringResource(R.string.runtime_available)) }
            items(versions?.remoteRuntime.orEmpty(), key = { "rt-$it" }) { version ->
                AvailableRow(version, active = versions?.activeRuntime == version) {
                    pendingDownload = version to false
                }
            }
            items(versions?.remoteWebUi.orEmpty(), key = { "ui-$it" }) { version ->
                AvailableRow(version, active = versions?.activeWebUi == version) {
                    pendingDownload = version to true
                }
            }

            if (!versions?.runtime.isNullOrEmpty() || !versions?.webUi.isNullOrEmpty()) {
                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                item { SectionLabel(stringResource(R.string.runtime_downloaded)) }
                items(versions?.runtime.orEmpty(), key = { "ir-${it.version}" }) { installed ->
                    DownloadedRow(installed) { pendingDelete = installed.version to false }
                }
                items(versions?.webUi.orEmpty(), key = { "iw-${it.version}" }) { installed ->
                    DownloadedRow(installed) { pendingDelete = installed.version to true }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun VersionRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun JobCard(job: RuntimeJob) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${job.kind} ${job.version}",
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(job.status, style = MaterialTheme.typography.labelSmall)
            }
            val percent = job.percent
            if (job.isLive) {
                if (percent != null) {
                    LinearProgressIndicator(progress = { (percent / 100.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Text(
                buildString {
                    append(job.stage)
                    if (percent != null) append(" · ${"%.0f".format(percent)}%")
                    if (job.totalBytes > 0) append(" · ${job.receivedBytes / 1_048_576}/${job.totalBytes / 1_048_576} MB")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (job.error.isNotBlank()) {
                Text(job.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AvailableRow(version: String, active: Boolean, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(version, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        if (active) {
            Text(stringResource(R.string.agent_status_active), style = MaterialTheme.typography.labelSmall)
        } else {
            TextButton(onClick = onDownload) { Text(stringResource(R.string.action_download)) }
        }
    }
}

@Composable
private fun DownloadedRow(item: RuntimeVersion, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(item.version, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        Text(item.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.active) {
            Text(
                stringResource(R.string.agent_status_active),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp)) }
    }
}
