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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The room's own workspace: the files its agents read and write.
 *
 * This is a separate tree from the session workspace — a room gets its own
 * directory under the Studio data folder — which is why it has its own screen
 * rather than reusing the session browser.
 *
 * Editing is deliberately plain: a text field over the whole file. Anything
 * binary is listed but not openable, because a hex dump on a phone helps nobody.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomFilesScreen(state: UiState, viewModel: AppViewModel) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val listing = state.roomFiles
    var creatingDir by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RoomWorkspaceEntry?>(null) }
    var deleting by remember { mutableStateOf<RoomWorkspaceEntry?>(null) }

    LaunchedEffect(state.openRoom?.id) {
        state.openRoom?.id?.let(viewModel::loadRoomFiles)
    }

    if (creatingDir) {
        TextPrompt(
            title = stringResource(R.string.room_files_new_folder),
            initial = "",
            onDismiss = { creatingDir = false },
            onConfirm = { name ->
                viewModel.createRoomFolder(state.roomFilesPath, name)
                creatingDir = false
            },
        )
    }
    renaming?.let { entry ->
        TextPrompt(
            title = stringResource(R.string.room_files_rename),
            initial = entry.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameRoomFile(entry.path, name)
                renaming = null
            },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.room_files_delete)) },
            text = { Text(entry.path) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRoomFile(entry); deleting = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.roomFilesPath.ifBlank { stringResource(R.string.room_files_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.roomFilesUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { creatingDir = true }) {
                        Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.room_files_new_folder))
                    }
                    IconButton(onClick = { state.openRoom?.id?.let(viewModel::loadRoomFiles) }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            if (state.roomFilesLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (!state.roomFilesLoading && listing.entries.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.room_files_empty),
                        fontSize = 12.sp,
                        color = inkTextMuted(inkDark),
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(listing.entries, key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (entry.isDir) viewModel.openRoomFolder(entry.path)
                                else viewModel.openRoomFile(entry.path)
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (entry.isDir) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = inkTextSecondary(inkDark),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.name,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!entry.isDir) {
                                Text(
                                    listOf(compactNumber(entry.size) + "B", entry.modified.take(10))
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    fontSize = 9.sp,
                                    color = inkTextMuted(inkDark),
                                )
                            }
                        }
                        IconButton(onClick = { renaming = entry }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.DriveFileRenameOutline,
                                stringResource(R.string.room_files_rename),
                                Modifier.size(15.dp),
                                tint = inkTextMuted(inkDark),
                            )
                        }
                        IconButton(onClick = { deleting = entry }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                stringResource(R.string.action_delete),
                                Modifier.size(15.dp),
                                tint = inkTextMuted(inkDark),
                            )
                        }
                    }
                }
            }
        }
    }

    // The editor is a full dialog: a text field inside a lazy list would fight
    // the list for scroll gestures.
    if (state.roomFileOpen != null) {
        RoomFileEditor(
            path = state.roomFileOpen!!.path,
            content = state.roomFileContent,
            saving = state.roomFileSaving,
            onSave = viewModel::saveRoomFile,
            onDismiss = viewModel::closeRoomFile,
        )
    }
}

@Composable
private fun RoomFileEditor(
    path: String,
    content: String,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(path, content) { mutableStateOf(content) }
    val dirty = text != content

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dirty) {
                    Text(
                        stringResource(R.string.room_files_unsaved),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 400.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
        },
        confirmButton = {
            Button(enabled = dirty && !saving, onClick = { onSave(text) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** A one-field prompt, used for "new folder" and "rename". */
@Composable
private fun TextPrompt(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
