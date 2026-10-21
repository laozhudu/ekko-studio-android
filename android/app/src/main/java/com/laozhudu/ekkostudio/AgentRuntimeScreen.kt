package com.laozhudu.ekkostudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentRuntimeScreen(state: UiState, viewModel: AppViewModel) {
    val families = listOf("hermes", "ekko", "coding")
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.agent_runtimes_title)) },
            navigationIcon = { IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            actions = { IconButton(onClick = viewModel::openAgentRuntimes) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) } },
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.agent_runtimes_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.loadingAgentRuntimes) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            families.forEach { family ->
                val runtimes = state.agentRuntimes.filter { it.family == family }
                if (runtimes.isNotEmpty()) {
                    item { Text(familyTitle(family), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(runtimes, key = { it.id }) { runtime -> RuntimeCard(runtime, viewModel, state) }
                }
            }
        }
    }
}

@Composable
private fun familyTitle(family: String) = stringResource(when (family) {
    "hermes" -> R.string.agent_family_hermes
    "ekko" -> R.string.agent_family_ekko
    else -> R.string.agent_family_coding
})

@Composable
private fun RuntimeCard(runtime: AgentRuntimeStatus, viewModel: AppViewModel, state: UiState) {
    // Coding agents carry an update policy and a model list that the other
    // families do not; those rows come from /coding-agents/update-policies and
    // /coding-agents/models.
    val policy = state.agentPolicies.firstOrNull { it.id == runtime.id }
    val models = state.codingAgentModels.firstOrNull { it.agentId == runtime.id }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = runtime.installed) { viewModel.startRuntimeConversation(runtime) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = when (runtime.family) { "hermes" -> Color(0xFF4CA66A); "ekko" -> Color(0xFF4CA66A); else -> Color(0xFF007AFF) }) {
                Icon(if (runtime.family == "coding") Icons.Filled.Code else Icons.Filled.Psychology, null, Modifier.padding(11.dp).size(23.dp), tint = Color.White)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(runtime.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(runtime.version, runtime.source.takeIf { it != "not-installed" }).joinToString(" · ").ifBlank { stringResource(R.string.agent_runtime_unavailable) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                runtime.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Surface(shape = CircleShape, color = if (runtime.installed) Color(0xFF4CA66A).copy(alpha = .16f) else MaterialTheme.colorScheme.errorContainer) {
                Text(
                    stringResource(if (runtime.installed) R.string.agent_runtime_ready else R.string.agent_runtime_not_installed),
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (runtime.installed) Color(0xFF27995A) else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        if (runtime.family == "coding") {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Version against what the registry offers: "0.2.1 → 0.25.3" is
                    // the only line that tells you whether checking is worth it.
                    Text(
                        listOfNotNull(
                            policy?.current?.takeIf { it.isNotBlank() } ?: runtime.version.takeIf { it.isNotBlank() },
                            policy?.latest?.takeIf { it.isNotBlank() && it != policy?.current },
                        ).joinToString(" → ").ifBlank { stringResource(R.string.agent_runtime_unavailable) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    models?.models?.takeIf { it.isNotEmpty() }?.let { list ->
                        Text(
                            stringResource(R.string.coding_agent_models, list.size, list.take(2).joinToString(", ")),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (policy?.autoUpdateSupported == true) {
                    Text(stringResource(R.string.coding_agent_auto_update), style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = policy.autoUpdate,
                        onCheckedChange = { viewModel.setCodingAgentAutoUpdate(runtime.id, it) },
                        modifier = Modifier.padding(start = 6.dp).scale(0.8f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (runtime.installed) {
                    TextButton(onClick = { viewModel.checkCodingAgentUpdate(runtime.id) }) {
                        Text(stringResource(R.string.coding_agent_check_update), fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { viewModel.updateCodingAgent(runtime.id) }) {
                    Text(
                        stringResource(
                            if (runtime.installed) R.string.coding_agent_update else R.string.coding_agent_install,
                        ),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
