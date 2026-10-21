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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Everything about a room that is not a message: who is in it, which agents
 * answer, and how it summarises and hands off.
 *
 * The official client splits these across a members list, an agent editor and a
 * config sheet. A phone has room for one screen with three tabs, and all three
 * write to routes that already exist — the reason they were missing before is
 * that the room-detail response fields were being thrown away, not that the API
 * could not do it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSettingsScreen(state: UiState, viewModel: AppViewModel) {
    val room = state.openRoom
    var tab by remember { mutableIntStateOf(0) }
    var editingAgent by remember { mutableStateOf<RoomAgent?>(null) }
    var addingAgent by remember { mutableStateOf(false) }
    var removingMember by remember { mutableStateOf<RoomMember?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var managingPresets by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.loadAgentPresets() }

    if (addingAgent || editingAgent != null) {
        RoomAgentDialog(
            initial = editingAgent,
            presets = state.agentPresets,
            profiles = state.profiles.map { it.name },
            onDismiss = { addingAgent = false; editingAgent = null },
            onConfirm = { draft ->
                val target = editingAgent
                if (target == null) viewModel.addRoomAgent(room?.id.orEmpty(), draft)
                else viewModel.updateRoomAgent(room?.id.orEmpty(), target, draft)
                addingAgent = false
                editingAgent = null
            },
        )
    }

    removingMember?.let { member ->
        AlertDialog(
            onDismissRequest = { removingMember = null },
            title = { Text(stringResource(R.string.room_remove_member)) },
            text = { Text(member.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeRoomMember(room?.id.orEmpty(), member)
                    removingMember = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { removingMember = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (managingPresets) {
        PresetManagerDialog(
            presets = state.agentPresets,
            profiles = state.profiles.map { it.name },
            onSave = { existing, draft -> viewModel.saveAgentPreset(existing, draft) },
            onDelete = { viewModel.deleteAgentPreset(it) },
            onDismiss = { managingPresets = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.room_clear_context)) },
            text = { Text(stringResource(R.string.room_clear_context_note)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearRoomContext(room?.id.orEmpty())
                    confirmClear = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: stringResource(R.string.room_settings)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                listOf(
                    stringResource(R.string.room_tab_agents),
                    stringResource(R.string.room_tab_members),
                    stringResource(R.string.room_tab_settings),
                ).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, fontSize = 12.sp) })
                }
            }
            when (tab) {
                0 -> AgentsTab(
                    room = room,
                    presets = state.agentPresets,
                    canManage = room?.canManage == true,
                    onAdd = { addingAgent = true },
                    onEdit = { editingAgent = it },
                    onDelete = { viewModel.deleteRoomAgent(room?.id.orEmpty(), it.id) },
                    onManagePresets = { managingPresets = true },
                )
                1 -> MembersTab(
                    room = room,
                    canManage = room?.canManage == true,
                    onCopyInvite = { room?.inviteCode?.let { clipboard.setText(AnnotatedString(it)) } },
                    onRegenerate = { room?.id?.let(viewModel::regenerateInviteCode) },
                    onRemove = { removingMember = it },
                )
                else -> SettingsTab(
                    room = room,
                    busy = state.busy,
                    onRename = { name -> room?.id?.let { viewModel.saveRoomConfig(it, name = name) } },
                    onHandoff = { enabled -> room?.id?.let { viewModel.saveRoomConfig(it, handoffEnabled = enabled) } },
                    onHandoffDepth = { depth -> room?.id?.let { viewModel.saveRoomConfig(it, handoffMaxDepth = depth) } },
                    onSummaryTurns = { turns -> room?.id?.let { viewModel.saveRoomConfig(it, summaryEveryTurns = turns) } },
                    onClear = { confirmClear = true },
                )
            }
        }
    }
}

@Composable
private fun AgentsTab(
    room: RoomDetail?,
    presets: List<AgentPreset>,
    canManage: Boolean,
    onAdd: () -> Unit,
    onEdit: (RoomAgent) -> Unit,
    onDelete: (RoomAgent) -> Unit,
    onManagePresets: () -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val agents = room?.agentList.orEmpty()
    Column(Modifier.fillMaxSize()) {
        if (!canManage) {
            Note(stringResource(R.string.room_agents_readonly))
        }
        // Presets are account-wide defaults, so they belong beside the add button
        // rather than inside it: you set them up once and reuse them per room.
        if (canManage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (presets.isEmpty()) stringResource(R.string.preset_none)
                    else stringResource(R.string.preset_count, presets.size),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = inkTextMuted(inkDark),
                )
                TextButton(onClick = onManagePresets) { Text(stringResource(R.string.preset_manage)) }
            }
        }
        if (agents.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.room_agents_empty),
                    fontSize = 12.sp,
                    color = inkTextMuted(inkDark),
                )
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(agents, key = { it.id }) { agent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = canManage) { onEdit(agent) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(agent.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (agent.isRemote) Badge(stringResource(R.string.room_agent_remote))
                            if (agent.isGlobal) Badge(stringResource(R.string.room_agent_global))
                        }
                        Text(
                            listOfNotNull(
                                agent.profile.takeIf { it.isNotBlank() },
                                agent.model.takeIf { it.isNotBlank() },
                                agent.reasoningEffort.takeIf { it.isNotBlank() },
                            ).joinToString(" · ").ifBlank { agent.agent },
                            fontSize = 11.sp,
                            color = inkTextMuted(inkDark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (canManage) {
                        IconButton(onClick = { onDelete(agent) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        if (canManage) {
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.room_agent_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MembersTab(
    room: RoomDetail?,
    canManage: Boolean,
    onCopyInvite: () -> Unit,
    onRegenerate: () -> Unit,
    onRemove: (RoomMember) -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.room_invite_code), fontSize = 11.sp, color = inkTextMuted(inkDark))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                room?.inviteCode.orEmpty().ifBlank { "—" },
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onCopyInvite) { Text(stringResource(R.string.action_copy)) }
            if (canManage) {
                TextButton(onClick = onRegenerate) { Text(stringResource(R.string.room_invite_regenerate)) }
            }
        }
        Text(
            stringResource(R.string.room_invite_note),
            fontSize = 10.sp,
            color = inkTextMuted(inkDark),
        )
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(
            stringResource(R.string.room_members_count, room?.members?.size ?: 0),
            fontSize = 11.sp,
            color = inkTextMuted(inkDark),
        )
        room?.members.orEmpty().forEach { member ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(member.name.ifBlank { member.userId }, null, size = 26.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(member.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (member.online) stringResource(R.string.devices_status_online)
                        else stringResource(R.string.devices_status_offline),
                        fontSize = 10.sp,
                        color = inkTextMuted(inkDark),
                    )
                }
                if (canManage && room?.members.orEmpty().size > 1) {
                    TextButton(onClick = { onRemove(member) }) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    room: RoomDetail?,
    busy: Boolean,
    onRename: (String) -> Unit,
    onHandoff: (Boolean) -> Unit,
    onHandoffDepth: (Int) -> Unit,
    onSummaryTurns: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    var name by remember(room?.id) { mutableStateOf(room?.name.orEmpty()) }
    val canManage = room?.canManage == true

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!canManage) Note(stringResource(R.string.room_settings_readonly))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.room_name)) },
            singleLine = true,
            enabled = canManage,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(enabled = canManage && !busy, onClick = { onRename(name) }) {
            Text(stringResource(R.string.action_save))
        }

        HorizontalDivider()

        // Handoff: whether one agent may pass the turn to another.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.room_handoff), fontSize = 13.sp)
                Text(
                    stringResource(R.string.room_handoff_note),
                    fontSize = 10.sp,
                    color = inkTextMuted(inkDark),
                )
            }
            Switch(
                checked = room?.handoffEnabled == true,
                enabled = canManage,
                onCheckedChange = { onHandoff(it) },
            )
        }
        if (room?.handoffEnabled == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 5).forEach { depth ->
                    FilterChip(
                        selected = room.handoffMaxDepth == depth,
                        enabled = canManage,
                        onClick = { onHandoffDepth(depth) },
                        label = { Text(stringResource(R.string.room_handoff_depth, depth)) },
                    )
                }
            }
        }

        HorizontalDivider()

        // Summary cadence: how many turns before the room compresses itself.
        Text(stringResource(R.string.room_summary_every), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 10, 20, 50).forEach { turns ->
                FilterChip(
                    selected = room?.summaryEveryTurns == turns,
                    enabled = canManage,
                    onClick = { onSummaryTurns(turns) },
                    label = {
                        Text(
                            if (turns == 0) stringResource(R.string.room_summary_off)
                            else stringResource(R.string.room_summary_turns, turns),
                        )
                    },
                )
            }
        }
        room?.summaryModel?.takeIf { it.isNotBlank() }?.let { model ->
            Text(
                stringResource(R.string.room_summary_model, model),
                fontSize = 10.sp,
                color = inkTextMuted(inkDark),
            )
        }

        HorizontalDivider()

        TextButton(enabled = canManage && !busy, onClick = onClear) {
            Text(stringResource(R.string.room_clear_context), color = inkError(inkDark))
        }
        room?.workspace?.takeIf { it.isNotBlank() }?.let { workspace ->
            Text(stringResource(R.string.room_workspace, workspace), fontSize = 10.sp, color = inkTextMuted(inkDark))
        }
    }
}

@Composable
private fun Note(text: String) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Info, null, Modifier.size(14.dp), tint = inkTextMuted(inkDark))
        Text(text, fontSize = 11.sp, color = inkTextMuted(inkDark))
    }
}

@Composable
private fun Badge(text: String) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(inkSelectedBg(inkDark))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 9.sp, color = inkTextSecondary(inkDark))
    }
}

/**
 * Add or edit one room agent.
 *
 * `agentMode` is not offered: the server derives it from the agent name (a
 * built-in global agent is global, everything else is room-scoped) and refuses a
 * model on a global one. Offering a switch the server would override is worse
 * than not offering it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomAgentDialog(
    initial: RoomAgent?,
    presets: List<AgentPreset>,
    profiles: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (RoomAgentDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var profile by remember { mutableStateOf(initial?.profile ?: profiles.firstOrNull().orEmpty()) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var provider by remember { mutableStateOf(initial?.provider.orEmpty()) }
    var effort by remember { mutableStateOf(initial?.reasoningEffort.orEmpty()) }
    var preset by remember { mutableStateOf(initial?.preset.orEmpty()) }
    val valid = name.isNotBlank() && profile.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.room_agent_add else R.string.room_agent_edit))
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(
                    description, { description = it },
                    label = { Text(stringResource(R.string.room_agent_description)) },
                    minLines = 2,
                )
                if (profiles.size > 1) {
                    Text(stringResource(R.string.room_agent_profile), fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.take(4).forEach { candidate ->
                            FilterChip(
                                selected = profile == candidate,
                                onClick = { profile = candidate },
                                label = { Text(candidate, fontSize = 11.sp) },
                            )
                        }
                    }
                }
                if (presets.isNotEmpty()) {
                    Text(stringResource(R.string.room_agent_preset), fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.take(3).forEach { candidate ->
                            FilterChip(
                                selected = preset == candidate.id,
                                onClick = {
                                    preset = candidate.id
                                    if (model.isBlank()) model = candidate.model
                                    if (provider.isBlank()) provider = candidate.provider
                                    if (effort.isBlank()) effort = candidate.reasoningEffort
                                },
                                label = { Text(candidate.name, fontSize = 11.sp) },
                            )
                        }
                    }
                }
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.room_agent_model)) }, singleLine = true)
                OutlinedTextField(provider, { provider = it }, label = { Text(stringResource(R.string.room_agent_provider)) }, singleLine = true)
                OutlinedTextField(
                    effort, { effort = it },
                    label = { Text(stringResource(R.string.room_agent_effort)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.room_agent_effort_hint)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        RoomAgentDraft(
                            name = name.trim(),
                            description = description.trim(),
                            profile = profile.trim(),
                            provider = provider.trim(),
                            model = model.trim(),
                            reasoningEffort = effort.trim(),
                            presetId = preset,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Account-wide agent defaults.
 *
 * A preset is a saved (profile, provider, model, effort) bundle that the add-agent
 * sheet can apply in one tap. The server stores them per account, not per room,
 * which is why this lives in the same dialog the room agent editor reads from.
 */
@Composable
private fun PresetManagerDialog(
    presets: List<AgentPreset>,
    profiles: List<String>,
    onSave: (AgentPreset?, RoomAgentDraft) -> Unit,
    onDelete: (AgentPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    var editing by remember { mutableStateOf<AgentPreset?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<AgentPreset?>(null) }

    if (creating || editing != null) {
        RoomAgentDialog(
            initial = editing?.let { preset ->
                // Reuse the agent editor: the fields are the same ones a preset holds.
                RoomAgent(
                    id = preset.id,
                    name = preset.name,
                    description = preset.description,
                    profile = preset.profile,
                    agent = preset.agent,
                    agentMode = preset.agentMode,
                    model = preset.model,
                    provider = preset.provider,
                    apiMode = preset.apiMode,
                    reasoningEffort = preset.reasoningEffort,
                    avatar = preset.avatar,
                )
            },
            presets = emptyList(),
            profiles = profiles,
            onDismiss = { creating = false; editing = null },
            onConfirm = { draft ->
                onSave(editing, draft)
                creating = false
                editing = null
            },
        )
    }

    confirmDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.preset_delete)) },
            text = { Text(preset.name) },
            confirmButton = {
                TextButton(onClick = { onDelete(preset); confirmDelete = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_manage)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (presets.isEmpty()) {
                    Text(
                        stringResource(R.string.preset_none),
                        fontSize = 12.sp,
                        color = inkTextMuted(inkDark),
                    )
                }
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    preset.profile.takeIf { it.isNotBlank() },
                                    preset.model.takeIf { it.isNotBlank() },
                                    preset.reasoningEffort.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                fontSize = 10.sp,
                                color = inkTextMuted(inkDark),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { editing = preset }) { Text(stringResource(R.string.action_edit)) }
                        IconButton(onClick = { confirmDelete = preset }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { creating = true }) { Text(stringResource(R.string.preset_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
