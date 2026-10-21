package com.laozhudu.ekkostudio

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The About page, laid out the way the official `pages/about` is laid out:
 * an 80dp mark, the name at 24sp/650, the version at 13sp muted, then one card
 * of rows separated by hairlines, each with a chevron.
 *
 * Content differs on purpose. The official page links its own website, GitHub
 * and store listing and checks the vendor's release channel; this app is a
 * personal client distributed as a GitHub release, so it shows the versions of
 * the three things it actually depends on (itself, Studio, Hermes) and links to
 * its own source. It also says plainly that it is not affiliated with the
 * vendor, which the official page has no reason to say.
 */
@Composable
fun AboutScreen(state: UiState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    var licences by remember { mutableStateOf(false) }

    if (licences) {
        LicencesDialog(onDismiss = { licences = false })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // `.about-brand`: 44 top, 12 gap, 36 bottom, centred.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The app's own mark, 80dp with a 20dp radius — the official uses the
            // vendor logo here; this build ships original artwork.
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                )
            }
            Text(
                stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight(650),
                color = inkTextPrimary(inkDark),
            )
            Text(
                versionLine(),
                fontSize = 13.sp,
                color = inkTextMuted(inkDark),
                fontFamily = FontFamily.Monospace,
            )
        }

        // `.about-card`: 1dp border, 16dp radius, rows 16/18 with hairlines.
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, inkBorder(inkDark), RoundedCornerShape(16.dp)),
        ) {
            AboutRow(
                label = stringResource(R.string.about_check_update),
                value = "",
                onClick = viewModel::requestAppUpdateCheck,
            )
            AboutRow(
                label = stringResource(R.string.about_studio_version),
                value = state.serverVersions?.studio?.ifBlank { null } ?: stringResource(R.string.about_unknown),
                onClick = viewModel::openStudioUpdate,
            )
            AboutRow(
                label = stringResource(R.string.about_hermes_version),
                value = state.serverVersions?.agent?.ifBlank { null } ?: stringResource(R.string.about_unknown),
                onClick = viewModel::openRuntimeVersions,
            )
            AboutRow(
                label = stringResource(R.string.about_source),
                value = "",
                onClick = {
                    // A personal fork: the only meaningful link is its own source.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/laozhudu/ekko-studio-android")),
                        )
                    }
                },
            )
            AboutRow(
                label = stringResource(R.string.about_licences),
                value = "",
                onClick = { licences = true },
            )
        }

        Text(
            stringResource(R.string.settings_about_note),
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 560.dp),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            color = inkTextMuted(inkDark),
        )
    }
}

/** "0.18.0 · 20261021" — the tag is what the update check compares, so show it. */
internal fun versionLine(versionName: String = BuildConfig.VERSION_NAME, commit: String = BuildConfig.BUILD_COMMIT): String {
    val parts = mutableListOf(versionName)
    if (commit != "local" && commit.isNotBlank()) parts += commit.take(7)
    return parts.joinToString(" · ")
}

@Composable
private fun AboutRow(label: String, value: String, onClick: () -> Unit) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            color = inkTextPrimary(inkDark),
        )
        if (value.isNotBlank()) {
            Text(
                value,
                fontSize = 13.sp,
                color = inkTextMuted(inkDark),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 10.dp).size(18.dp),
            tint = inkTextMuted(inkDark),
        )
    }
}

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_licences)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This client: MIT. Based on hermes-studio-mobile by mrz2333 (MIT), " +
                        "with changes by laozhudu. Hermes Agent is by Nous Research. " +
                        "Ekko Studio is by its respective authors; this app is not affiliated with it " +
                        "and ships none of its assets.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    "Avatars: Multiavatar.com.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}
