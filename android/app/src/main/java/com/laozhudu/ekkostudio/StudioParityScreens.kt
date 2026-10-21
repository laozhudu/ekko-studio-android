package com.laozhudu.ekkostudio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ParityScaffold(title: String, vm: AppViewModel, content: @Composable ColumnScope.() -> Unit) = Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }) }) { pad -> Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

@Composable fun JourneyScreen(state: UiState, vm: AppViewModel) = ParityScaffold(stringResource(R.string.journey_title), vm) { val graph = state.journey; val usage = state.skillUsage; Text("${graph?.profile.orEmpty()} · ${graph?.nodes?.size ?: 0} nodes · ${graph?.edges?.size ?: 0} links", fontWeight = FontWeight.Bold); usage?.let { Text("${it.totalActions} actions · ${it.totalLoads} loads · ${it.totalEdits} edits · ${it.distinctSkills} skills") }; LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { graph?.clusters?.let { items(it) { cluster -> Text("${cluster.first}: ${cluster.second}", style = MaterialTheme.typography.titleSmall) } }; graph?.nodes?.let { items(it, key = { n -> n.id }) { node -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(node.label, fontWeight = FontWeight.Bold); Text("${node.kind} · ${node.category} · ${node.uses}") } } } }; usage?.topSkills?.let { items(it) { skill -> Text("${skill.first}: ${skill.second}") } } } }

@Composable fun WebhooksScreen(state: UiState, vm: AppViewModel) { var edit by remember { mutableStateOf<WebhookEndpoint?>(null) };var add by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }; if(add||edit!=null) AlertDialog(onDismissRequest={add=false;edit=null}, title={Text(stringResource(R.string.webhooks_add))}, text={Column { OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.name))}); OutlinedTextField(url,{url=it},label={Text("URL")}) }}, confirmButton={TextButton(onClick={val item=edit;add=false;edit=null;if(item==null)vm.createWebhook(name,url) else vm.updateWebhook(item,name,url)}){Text(stringResource(R.string.action_save))}}, dismissButton={TextButton(onClick={add=false;edit=null}){Text(stringResource(R.string.action_cancel))}}); ParityScaffold(stringResource(R.string.webhooks_title),vm) { Row(verticalAlignment=Alignment.CenterVertically){Button(onClick={name="";url="";add=true}){Text(stringResource(R.string.action_add))}; Spacer(Modifier.width(8.dp)); TextButton(onClick=vm::clearWebhookEvents){Text(stringResource(R.string.clear_events))}}; LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){ items(state.webhooks,key={it.id}){hook->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(hook.name,fontWeight=FontWeight.Bold);Text(hook.url);Text("${hook.state} · ✓${hook.delivered} · !${hook.failed}");Row{Switch(hook.enabled,{vm.toggleWebhook(hook)});TextButton(onClick={name=hook.name;url=hook.url;edit=hook}){Text(stringResource(R.string.action_edit))};TextButton(onClick={vm.testWebhook(hook)}){Text(stringResource(R.string.action_test))};TextButton(onClick={vm.deleteWebhook(hook)}){Text(stringResource(R.string.action_delete))}}}}}; if(state.webhookEvents.isNotEmpty()) item{Text(stringResource(R.string.local_events),fontWeight=FontWeight.Bold)}; items(state.webhookEvents){Text(it)} } } }

