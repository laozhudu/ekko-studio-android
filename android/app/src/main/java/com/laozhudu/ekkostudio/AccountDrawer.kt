package com.laozhudu.ekkostudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The drawer behind the avatar: who you are signed in as, which Studio, the
 * model in use, and the account-level actions.
 *
 * The official client puts its whole session list in this drawer and has no
 * bottom tabs. This app keeps the tabs (they are one tap and reachable with a
 * thumb) and uses the drawer only for the account block, which is where the
 * official drawer's account menu also lives. That is a deliberate deviation,
 * recorded in docs/official-parity-plan.md.
 */
@Composable
fun AccountDrawerContent(
    profile: String,
    avatar: AvatarSpec?,
    serverHost: String,
    model: String?,
    profileCount: Int,
    onSwitchProfile: () -> Unit,
    onDevices: () -> Unit,
    onSettings: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp).fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile.ifBlank { "default" }, avatar, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.drawer_signed_in_as, profile.ifBlank { "default" }),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The host, not the full URL: the scheme and port are noise here.
                    Text(
                        serverHost,
                        fontSize = 11.sp,
                        color = inkTextMuted(inkDark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    model?.takeIf { it.isNotBlank() }?.let { active ->
                        Text(
                            active,
                            fontSize = 11.sp,
                            color = inkTextMuted(inkDark),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            if (profileCount > 1) {
                DrawerRow(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.drawer_switch_profile, profileCount),
                    onClick = onSwitchProfile,
                )
            }
            DrawerRow(Icons.Filled.Dns, stringResource(R.string.devices_title), onDevices)
            DrawerRow(Icons.Filled.Settings, stringResource(R.string.settings_title), onSettings)
            DrawerRow(Icons.Filled.Palette, stringResource(R.string.appearance_title), onAppearance)
            DrawerRow(Icons.Filled.Info, stringResource(R.string.settings_section_about), onAbout)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            DrawerRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = stringResource(R.string.drawer_sign_out),
                onClick = onSignOut,
                danger = true,
            )
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val tint = if (danger) inkError(inkDark) else inkTextSecondary(inkDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (danger) inkError(inkDark) else inkTextPrimary(inkDark),
        )
        if (!danger) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = inkTextMuted(inkDark),
            )
        }
    }
}

/** `https://192.168.99.226:8648` → `192.168.99.226:8648`, for the drawer header. */
internal fun serverHostOf(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    return withoutScheme.ifBlank { trimmed }.take(60)
}
