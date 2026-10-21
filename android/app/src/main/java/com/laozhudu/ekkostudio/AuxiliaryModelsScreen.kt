package com.laozhudu.ekkostudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Per-task model overrides and the subagent default.
 *
 * The server has fifteen auxiliary slots — vision, compression, chat titles, the
 * curator, and so on — and each may run on its own model instead of inheriting the
 * main one. That is the difference between "compressing this conversation costs a
 * frontier model" and "it costs a cheap one", so it is worth a screen.
 *
 * Only the fields the server's normaliser accepts are offered: provider, model,
 * base_url, timeout. The `api_key` field exists on the wire but is deliberately not
 * shown — a key typed into a phone chat app is a key in a screenshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuxiliaryModelsScreen(state: UiState, viewModel: AppViewModel) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val config = state.auxiliaryConfig
    // Local draft: the PUT replaces the whole map, so editing must be explicit.
    var draft by remember(config) { mutableStateOf(config.assignments) }
    var editing by remember { mutableStateOf<AuxiliaryTask?>(null) }
    var delegationProvider by remember(state.delegation) { mutableStateOf(state.delegation.provider) }
    var delegationModel by remember(state.delegation) { mutableStateOf(state.delegation.model) }
    var delegationEffort by remember(state.delegation) { mutableStateOf(state.delegation.reasoningEffort) }
    val dirty = draft != config.assignments ||
        delegationProvider != state.delegation.provider ||
        delegationModel != state.delegation.model ||
        delegationEffort != state.delegation.reasoningEffort

    LaunchedEffect(Unit) { viewModel.loadAuxiliaryConfig() }

    editing?.let { task ->
        AuxiliaryEditor(
            task = task,
            initial = draft[task.key] ?: AuxiliaryAssignment(),
            onDismiss = { editing = null },
            onSave = { assignment ->
                draft = if (assignment.isInherited) draft - task.key else draft + (task.key to assignment)
                editing = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auxiliary_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAuxiliaryConfig() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            if (state.loadingAuxiliary) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                stringResource(R.string.auxiliary_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = inkTextMuted(inkDark),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "delegation") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            stringResource(R.string.auxiliary_delegation),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.auxiliary_delegation_note),
                            fontSize = 10.sp,
                            color = inkTextMuted(inkDark),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = delegationProvider,
                                onValueChange = { delegationProvider = it },
                                label = { Text(stringResource(R.string.auxiliary_provider), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = delegationModel,
                                onValueChange = { delegationModel = it },
                                label = { Text(stringResource(R.string.auxiliary_model), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("", "minimal", "low", "medium", "high").forEach { effort ->
                                FilterChip(
                                    selected = delegationEffort == effort,
                                    onClick = { delegationEffort = effort },
                                    label = {
                                        Text(
                                            effort.ifBlank { stringResource(R.string.auxiliary_effort_default) },
                                            fontSize = 10.sp,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }

                items(config.tasks, key = { it.key }) { task ->
                    val assignment = draft[task.key]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { editing = task }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(task.label, fontSize = 13.sp)
                            Text(
                                if (assignment == null || assignment.isInherited) {
                                    stringResource(R.string.auxiliary_inherit)
                                } else {
                                    listOf(
                                        assignment.provider.ifBlank { "?" },
                                        assignment.model.ifBlank { "?" },
                                        assignment.timeout.takeIf { it > 0 }?.let { "${it}s" } ?: "",
                                    ).filter { it.isNotBlank() }.joinToString(" · ")
                                },
                                fontSize = 10.sp,
                                color = inkTextMuted(inkDark),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            task.defaultTimeout.takeIf { it > 0 }?.let { "${it}s" } ?: "",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = inkTextMuted(inkDark),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    enabled = draft.isNotEmpty(),
                    onClick = { draft = emptyMap() },
                ) { Text(stringResource(R.string.auxiliary_reset)) }
                Button(
                    enabled = dirty && !state.loadingAuxiliary,
                    onClick = {
                        viewModel.saveAuxiliaryConfig(
                            assignments = draft,
                            delegation = DelegationConfig(delegationProvider.trim(), delegationModel.trim(), delegationEffort),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun AuxiliaryEditor(
    task: AuxiliaryTask,
    initial: AuxiliaryAssignment,
    onDismiss: () -> Unit,
    onSave: (AuxiliaryAssignment) -> Unit,
) {
    var provider by remember { mutableStateOf(initial.provider) }
    var model by remember { mutableStateOf(initial.model) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var timeout by remember { mutableStateOf(initial.timeout.takeIf { it > 0 }?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.label) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    provider, { provider = it },
                    label = { Text(stringResource(R.string.auxiliary_provider)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.auxiliary_provider_hint), fontSize = 9.sp) },
                )
                OutlinedTextField(
                    model, { model = it },
                    label = { Text(stringResource(R.string.auxiliary_model)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    baseUrl, { baseUrl = it },
                    label = { Text(stringResource(R.string.auxiliary_base_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    timeout,
                    { raw -> timeout = raw.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.auxiliary_timeout)) },
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(R.string.auxiliary_timeout_hint, task.defaultTimeout), fontSize = 9.sp)
                    },
                )
                Text(
                    stringResource(R.string.auxiliary_clear_hint),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    AuxiliaryAssignment(
                        provider = provider.trim(),
                        model = model.trim(),
                        baseUrl = baseUrl.trim(),
                        timeout = timeout.toIntOrNull() ?: 0,
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(AuxiliaryAssignment()) }) {
                    Text(stringResource(R.string.auxiliary_clear))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
