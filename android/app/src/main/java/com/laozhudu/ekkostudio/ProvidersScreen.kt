package com.laozhudu.ekkostudio

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale

/**
 * Signing in to the model providers.
 *
 * Two flows, both driven by the same screen: a device code you type on another
 * device (Nous, Copilot) and an authorization URL you approve in a browser
 * (Anthropic, Codex, xAI, MiniMax). Neither can be completed on the phone alone
 * in the first case, or without a browser in the second — so the sheet's job is to
 * make the handoff obvious and then get out of the way while it polls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(state: UiState, viewModel: AppViewModel) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val flow = state.authFlow

    LaunchedEffect(Unit) { viewModel.loadProviders() }

    flow?.let { active ->
        var code by remember(active.sessionId) { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = viewModel::closeProviderAuth,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(active.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        if (active.userCode != null) R.string.provider_device_code_note
                        else R.string.provider_authorize_note,
                    ),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = inkTextMuted(inkDark),
                )

                // The code is the one thing that has to survive a switch to another
                // device, so it is large, monospace, and tappable to copy.
                active.userCode?.let { userCode ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { clipboard.setText(AnnotatedString(userCode)) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            userCode,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        )
                    }
                    Text(
                        stringResource(R.string.provider_copy_code),
                        fontSize = 10.sp,
                        color = inkTextMuted(inkDark),
                    )
                }

                val target = active.userCode?.let { active.verificationUrl } ?: active.authorizationUrl
                if (target.isNotBlank()) {
                    TextButton(
                        onClick = { openExternalLink(context, target) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.provider_open_browser), Modifier.padding(start = 8.dp))
                    }
                }

                if (active.supportsSubmit) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.trim() },
                        label = { Text(stringResource(R.string.provider_paste_code)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        enabled = code.isNotBlank() && active.status != "submitted",
                        onClick = { viewModel.submitProviderCode(code) },
                    ) { Text(stringResource(R.string.provider_submit)) }
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        active.polling -> {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.provider_waiting), fontSize = 11.sp)
                        }
                        active.finished -> {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = inkSuccess(inkDark))
                            Text(stringResource(R.string.provider_done), fontSize = 11.sp)
                        }
                        active.failed -> {
                            Text(
                                active.error ?: active.status,
                                fontSize = 11.sp,
                                color = inkError(inkDark),
                            )
                        }
                        else -> Text(active.status, fontSize = 11.sp, color = inkTextMuted(inkDark))
                    }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = viewModel::closeProviderAuth) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.providers_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadProviders) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            Text(
                stringResource(R.string.providers_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = inkTextMuted(inkDark),
            )
            if (state.loadingProviders && state.providerStatuses.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(OAUTH_PROVIDERS, key = { it.id }) { provider ->
                    val status = state.providerStatuses.firstOrNull { it.provider == provider.id }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    status == null -> stringResource(R.string.provider_unknown)
                                    status.authenticated -> stringResource(R.string.provider_signed_in_short)
                                    status.reloginRequired -> stringResource(R.string.provider_relogin)
                                    else -> stringResource(R.string.provider_signed_out)
                                },
                                fontSize = 10.sp,
                                color = if (status?.authenticated == true) inkSuccess(inkDark) else inkTextMuted(inkDark),
                            )
                            status?.source?.takeIf { it.isNotBlank() }?.let { source ->
                                Text(source, fontSize = 9.sp, color = inkTextMuted(inkDark))
                            }
                        }
                        if (provider.toggles && status?.authenticated == true) {
                            Switch(
                                checked = status.enabled,
                                onCheckedChange = viewModel::setCopilotEnabled,
                                modifier = Modifier.scale(0.8f),
                            )
                        }
                        TextButton(onClick = { viewModel.startProviderAuth(provider) }) {
                            Text(
                                stringResource(
                                    if (status?.authenticated == true) R.string.provider_sign_in_again
                                    else R.string.provider_sign_in,
                                ),
                                fontSize = 12.sp,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}
