package xyz.rflg.hstudiodirect

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FilesScreen(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    var folderDialog by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf<StudioFile?>(null) }
    var rename by remember { mutableStateOf<StudioFile?>(null) }
    var copy by remember { mutableStateOf<StudioFile?>(null) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        vm.uploadStudioFile(bytes, uri.lastPathSegment?.substringAfterLast('/') ?: "upload", context.contentResolver.getType(uri) ?: "application/octet-stream")
    }
    if (folderDialog) SimpleNameDialog(stringResource(R.string.files_new_folder), "", { folderDialog = false }) { folderDialog = false; vm.createStudioFolder(it) }
    rename?.let { file -> SimpleNameDialog(stringResource(R.string.action_rename), file.name, { rename = null }) { rename = null; vm.renameStudioFile(file, it) } }
    copy?.let { file -> SimpleNameDialog(stringResource(R.string.files_copy_to), file.path, { copy = null }) { copy = null; vm.copyStudioFile(file, it) } }
    manage?.let { file -> AlertDialog(onDismissRequest = { manage = null }, title = { Text(file.name) }, text = { Column { TextButton(onClick = { manage = null; vm.openStudioFile(file) }) { Text(stringResource(R.string.files_edit)) }; TextButton(onClick = { rename = file; manage = null }) { Text(stringResource(R.string.action_rename)) }; TextButton(onClick = { copy = file; manage = null }) { Text(stringResource(R.string.files_copy_to)) }; TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.studioFileUrl(file)))); manage = null }) { Text(stringResource(R.string.files_download)) }; TextButton(onClick = { vm.deleteStudioFile(file); manage = null }) { Text(stringResource(R.string.action_delete)) } } }, confirmButton = {}) }
    state.openFile?.let { file -> var content by remember(file.path, state.openFileContent) { mutableStateOf(state.openFileContent) }; AlertDialog(onDismissRequest = vm::closeStudioFile, title = { Text(file.name) }, text = { OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), minLines = 12, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }, confirmButton = { TextButton(onClick = { vm.saveStudioFile(content) }) { Text(stringResource(R.string.action_save)) } }, dismissButton = { TextButton(onClick = vm::closeStudioFile) { Text(stringResource(R.string.action_cancel)) } }) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.files_title)) }, navigationIcon = { IconButton(onClick = { if (state.filesPath.isBlank()) vm.back() else vm.openFiles(state.filesPath.substringBeforeLast('/', "")) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }, actions = { IconButton(onClick = { upload.launch("*/*") }) { Icon(Icons.Filled.Upload, stringResource(R.string.files_upload)) }; IconButton(onClick = { folderDialog = true }) { Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.files_new_folder)) }; IconButton(onClick = { vm.openFiles(state.filesPath) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilesBreadcrumb(state.filesPath, vm) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) } }
            if (state.studioFiles.isEmpty() && state.error == null) item { Text(stringResource(R.string.files_empty), modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.studioFiles, key = { it.path }) { file ->
                // HStudio workspace-file-row: min-height 48px, padding 5px 7px,
                // gap 7px, radius 5px; icon 19px @0.82 opacity; name 13px/19px.
                val rowShape = RoundedCornerShape(5.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(rowShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { if (file.directory) vm.openFiles(file.path) else manage = file }
                        .padding(start = 10.dp, top = 5.dp, end = 7.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (file.directory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = if (file.directory) stringResource(R.string.files_directory) else stringResource(R.string.files_file),
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    )
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!file.directory) Text(
                            "${file.size} B",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { manage = file }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.files_more_actions)) }
                }
            }
        }
    }
}

@Composable private fun FilesBreadcrumb(path: String, vm: AppViewModel) {
    val parts = path.split('/').filter(String::isNotBlank)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.openFiles("") }) { Text("/") }
        parts.forEachIndexed { index, part ->
            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.openFiles(parts.take(index + 1).joinToString("/")) }) { Text(part, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LogsScreen(state: UiState, vm: AppViewModel) { Scaffold(topBar = { TopAppBar(title = { Text(state.openLog?.name ?: stringResource(R.string.logs_title)) }, navigationIcon = { IconButton(onClick = { if (state.openLog != null) vm.closeLog() else vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }, actions = { IconButton(onClick = { state.openLog?.let(vm::openLog) ?: vm.openLogs() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) } }) }) { pad -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (state.openLog == null) items(state.studioLogs, key = { it.name }) { log -> Card(Modifier.fillMaxWidth().clickable { vm.openLog(log) }) { Column(Modifier.padding(15.dp)) { Text(log.name, fontWeight = FontWeight.Bold); Text("${log.size} · ${log.modified}", style = MaterialTheme.typography.labelSmall) } } } else items(state.logEntries) { entry -> Column { Text("${entry.level} · ${entry.timestamp}", color = if (entry.level.contains("ERROR")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text(entry.message, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall); HorizontalDivider() } } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ConnectionsScreen(state: UiState, vm: AppViewModel) { var revoke by remember { mutableStateOf<AppConnection?>(null) }; var manual by remember { mutableStateOf(false) }; revoke?.let { item -> AlertDialog(onDismissRequest = { revoke = null }, title = { Text(stringResource(R.string.revoke_connection)) }, text = { Text(item.name) }, confirmButton = { TextButton(onClick = { revoke = null; vm.revokeAppConnection(item) }) { Text(stringResource(R.string.action_delete)) } }, dismissButton = { TextButton(onClick = { revoke = null }) { Text(stringResource(R.string.action_cancel)) } }) }; if (manual) SimpleNameDialog(stringResource(R.string.manual_device), "", { manual = false }) { manual = false; vm.manualDeviceRequest(it) }; Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.connections_title)) }, navigationIcon = { IconButton(vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }, actions = { IconButton(vm::openConnections) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) } }) }) { pad -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {  if (state.appConnections.isNotEmpty()) item { Text(stringResource(R.string.app_connections_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; items(state.appConnections, key = { it.id }) { connection -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(connection.name, fontWeight = FontWeight.Bold); Text("${connection.type} · ${if (connection.online) stringResource(R.string.connected) else stringResource(R.string.disconnected)}") }; TextButton(onClick = { revoke = connection }) { Text(stringResource(R.string.action_delete)) } } } }; item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.devices_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); TextButton(onClick = { manual = true }) { Text(stringResource(R.string.manual_device)) } } }; if (state.pairingLink.isNotBlank()) item { Card { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { QrCode(state.pairingLink); Text(state.pairingLink, style = MaterialTheme.typography.labelSmall) } } }; items(state.studioDevices, key = { it.id }) { device -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text(device.name, fontWeight = FontWeight.Bold); Text(device.address); Text("${device.inbound} · ${device.outbound}", style = MaterialTheme.typography.labelSmall); Row { TextButton(onClick = { vm.deviceAction(device, "approve") }) { Text(stringResource(R.string.action_approve)) }; TextButton(onClick = { vm.deviceAction(device, if (device.inbound == "blocked") "unblock" else "block") }) { Text(stringResource(if (device.inbound == "blocked") R.string.unblock else R.string.block)) }; TextButton(onClick = { vm.deviceAction(device, "connect") }) { Text(stringResource(R.string.connect)) } } } } }; items(state.peerConnections, key = { it.id }) { peer -> Card { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(peer.name, Modifier.weight(1f)); TextButton(onClick = { vm.disconnectPeer(peer) }) { Text(stringResource(R.string.disconnect)) } } } } } } }

@Composable private fun QrCode(value: String) { if (value.isBlank()) return; val image = remember(value) { val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 420, 420); Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888).apply { for (y in 0 until 420) for (x in 0 until 420) setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE) }.asImageBitmap() }; Image(image, null, Modifier.size(190.dp)) }

@Composable private fun SimpleNameDialog(title: String, initial: String, dismiss: () -> Unit, save: (String) -> Unit) { var value by remember { mutableStateOf(initial) }; AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }) }, confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { save(value) }) { Text(stringResource(R.string.action_save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } }) }
