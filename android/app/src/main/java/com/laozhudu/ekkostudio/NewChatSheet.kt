package com.laozhudu.ekkostudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The sheet behind the compose button: file the new conversation into a
 * category and pick its starting workspace, then create it.
 *
 * Both values travel on the `run` payload rather than being PATCHed afterwards,
 * because a session does not exist until a run creates it — there is no id to
 * attach them to before the first message. The official client does the same
 * thing: its run payload carries `category_id`.
 *
 * The sheet is skippable: an empty selection means "the same as before", which
 * is what the plain compose button used to do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatSheet(
    categories: List<SessionCategory>,
    selectedCategoryId: Int?,
    workspace: String?,
    favouriteDirectories: List<String>,
    onSelectCategory: (Int?) -> Unit,
    onWorkspaceChange: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    var workspaceText by remember { mutableStateOf(workspace.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.action_new_chat),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                stringResource(R.string.session_new_choose_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategoryChoice(
                label = stringResource(R.string.session_group_uncategorised),
                selected = selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
            )
            categories.forEach { category ->
                CategoryChoice(
                    label = category.name,
                    selected = selectedCategoryId == category.id,
                    onClick = { onSelectCategory(category.id) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            OutlinedTextField(
                value = workspaceText,
                onValueChange = {
                    workspaceText = it
                    onWorkspaceChange(it)
                },
                label = { Text(stringResource(R.string.session_new_choose_workspace)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (favouriteDirectories.isNotEmpty()) {
                // Favourites double as the shortcut list here: typing a full server
                // path on a phone keyboard is the kind of thing people get wrong.
                favouriteDirectories.take(6).forEach { path ->
                    Text(
                        path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                workspaceText = path
                                onWorkspaceChange(path)
                            }
                            .padding(vertical = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_new_chat))
            }
        }
    }
}

@Composable
private fun CategoryChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.padding(end = 2.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
