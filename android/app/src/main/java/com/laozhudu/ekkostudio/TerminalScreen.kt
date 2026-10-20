package com.laozhudu.ekkostudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal terminal: raw pty output on top, a command line at the bottom, plus the
 * three keys a phone keyboard cannot send (Ctrl+C, Tab, Enter is the send button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    output: String,
    connected: Boolean,
    connecting: Boolean,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
) {
    var command by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }

    val status = when {
        connecting -> stringResource(R.string.terminal_status_connecting)
        connected -> stringResource(R.string.terminal_status_connected)
        else -> stringResource(R.string.terminal_status_disconnected)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${stringResource(R.string.terminal_title)} · $status")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.terminal_clear)) }
                    TextButton(onClick = onReconnect) {
                        Text(stringResource(R.string.terminal_reconnect))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0B0F12))
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = output.ifBlank { "—" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFD6E3EA),
                    modifier = Modifier.padding(10.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.terminal_hint)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
                Button(
                    onClick = {
                        onInput(command + "\r")
                        command = ""
                    },
                ) { Text("↵") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { onInput("\u0003") }) { Text("Ctrl+C") }
                TextButton(onClick = { onInput("\t") }) { Text("Tab") }
                TextButton(onClick = { onInput("\u001b[A") }) { Text("↑") }
                TextButton(onClick = { onInput("\u001b[B") }) { Text("↓") }
                TextButton(onClick = { onInput("clear\r") }) { Text("clear") }
                TextButton(onClick = { onInput("ls -lh\r") }) { Text("ls") }
            }
        }
    }
}
