package com.laozhudu.ekkostudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Appearance, split the way the official client splits it.
 *
 * Two different things used to live on one screen with no label: the text
 * scale and light/dark choice, which are stored on this phone, and the
 * font size, colours and background, which are written to the server and so
 * change every other device too. Reading the official page copy ("only saved
 * on this phone, does not overwrite the server theme") made the distinction
 * explicit, so the sections here say which is which.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(state: UiState, vm: AppViewModel) {
    val theme = state.themeSettings
    val context = LocalContext.current
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        vm.uploadThemeBackground(bytes, uri.lastPathSegment ?: "background", context.contentResolver.getType(uri) ?: "image/jpeg")
    }
    var fontSize by remember(theme) { mutableStateOf((theme?.fontSize ?: 16).toString()) }
    var textColor by remember(theme) { mutableStateOf(theme?.textColor.orEmpty()) }
    var accentColor by remember(theme) { mutableStateOf(theme?.accentColor.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.appearance_local), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.appearance_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.text_scale_title), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    0.9f to R.string.text_scale_small,
                    1f to R.string.text_scale_normal,
                    1.15f to R.string.text_scale_large,
                    1.3f to R.string.text_scale_xl,
                ).forEach { (scale, label) ->
                    FilterChip(
                        selected = state.textScale == scale,
                        onClick = { vm.setTextScale(scale) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            Text(stringResource(R.string.appearance_server), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.appearance_server_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                fontSize,
                { raw -> fontSize = raw.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.font_size)) },
                singleLine = true,
            )
            OutlinedTextField(textColor, { textColor = it }, label = { Text(stringResource(R.string.text_color)) }, singleLine = true)
            OutlinedTextField(accentColor, { accentColor = it }, label = { Text(stringResource(R.string.accent_color)) }, singleLine = true)
            Button(
                onClick = { vm.saveTheme(fontSize.toIntOrNull() ?: 16, textColor, accentColor) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }

            TextButton(onClick = { pickBackground.launch("image/*") }) {
                Text(stringResource(R.string.upload_background))
            }
            val backgroundName = theme?.backgroundName.orEmpty()
            if (backgroundName.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(backgroundName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::removeThemeBackground) { Text(stringResource(R.string.remove_background)) }
                }
            }
        }
    }
}
