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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource

/**
 * Read-only browser for the folder the current conversation works in.
 *
 * The path shown is relative to the profile root, because that is what
 * `/api/studio/files/…` resolves against; the absolute workspace from the
 * session is translated by [AppViewModel.openWorkspace].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    path: String,
    entries: List<WorkspaceEntry>,
    loading: Boolean,
    fileName: String?,
    fileContent: String?,
    onOpenDir: (WorkspaceEntry) -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onUp: () -> Unit,
    onCloseFile: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (WorkspaceEntry) -> Unit,
    onBack: () -> Unit,
) {
    val previewing = fileContent != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (previewing) {
                                fileName.orEmpty()
                            } else {
                                stringResource(R.string.workspace_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!previewing) {
                            Text(
                                "/" + path.ifBlank { "" },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (previewing) onCloseFile() else if (path.isBlank()) onBack() else onUp()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!previewing) {
                        IconButton(onClick = onRefresh, enabled = !loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && entries.isEmpty() && !previewing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                previewing -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SelectionContainer {
                            Text(
                                text = fileContent.orEmpty(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onCloseFile) {
                                Text(stringResource(R.string.action_back))
                            }
                        }
                    }
                }
                entries.isEmpty() -> {
                    Text(
                        stringResource(R.string.workspace_empty),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    ) {
                        items(entries, key = { it.path }) { entry ->
                            WorkspaceRow(
                                entry = entry,
                                onClick = { if (entry.isDir) onOpenDir(entry) else onOpenFile(entry) },
                                onDownload = { onDownload(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(entry: WorkspaceEntry, onClick: () -> Unit, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Filled.Folder else Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDir) {
                Text(
                    byteLabel(entry.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!entry.isDir) {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.action_download),
                )
            }
        }
    }
}

private fun byteLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
