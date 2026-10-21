package com.laozhudu.ekkostudio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model pricing, in USD per million tokens.
 *
 * The server replaces the whole table on save, so this screen edits a local
 * draft and pushes it in one request. An empty row is dropped rather than
 * rejected, because a half-typed provider happens during normal editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(state: UiState, vm: AppViewModel) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (adding || editing != null) {
        val index = editing
        val existing = index?.let { state.pricingRates.getOrNull(it) }
        PricingRateDialog(
            initial = existing,
            onDismiss = { adding = false; editing = null },
            onConfirm = { rate ->
                vm.upsertPricingRate(index, rate)
                adding = false
                editing = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pricing_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.pricingDirty) {
                        Button(
                            onClick = vm::savePricing,
                            enabled = !state.pricingSaving,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            if (state.pricingSaving) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                stringResource(R.string.pricing_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.pricingDirty) {
                Text(
                    stringResource(R.string.pricing_unsaved),
                    style = MaterialTheme.typography.labelMedium,
                    color = inkWarning(inkDark),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.pricingRates.isEmpty()) {
                Text(
                    stringResource(R.string.pricing_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.pricingRates, key = { index, _ -> index }) { index, rate ->
                    Card(
                        Modifier.fillMaxWidth().clickable { editing = index },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    rate.model,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    rate.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    rateSummary(rate),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(onClick = { vm.removePricingRate(index) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.pricing_add), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** "in 3 / out 15 / cache 0.3 / 3.75" — only the fields that are set. */
internal fun rateSummary(rate: PricingRate): String {
    val parts = mutableListOf("in ${trimNumber(rate.input)}", "out ${trimNumber(rate.output)}")
    rate.cacheRead?.let { parts += "r ${trimNumber(it)}" }
    rate.cacheWrite?.let { parts += "w ${trimNumber(it)}" }
    return parts.joinToString(" · ")
}

/** 3.0 reads as "3", 0.25 stays "0.25" — prices are compared by eye. */
internal fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun PricingRateDialog(initial: PricingRate?, onDismiss: () -> Unit, onConfirm: (PricingRate) -> Unit) {
    var provider by remember { mutableStateOf(initial?.provider.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var input by remember { mutableStateOf(initial?.input?.let(::trimNumber).orEmpty()) }
    var output by remember { mutableStateOf(initial?.output?.let(::trimNumber).orEmpty()) }
    var cacheRead by remember { mutableStateOf(initial?.cacheRead?.let(::trimNumber).orEmpty()) }
    var cacheWrite by remember { mutableStateOf(initial?.cacheWrite?.let(::trimNumber).orEmpty()) }
    val valid = provider.isNotBlank() && model.isNotBlank() &&
        input.toDoubleOrNull() != null && output.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.pricing_add else R.string.action_edit)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(provider, { provider = it }, label = { Text(stringResource(R.string.pricing_provider)) }, singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.pricing_model)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(input, { input = it }, stringResource(R.string.pricing_input), Modifier.weight(1f))
                    NumberField(output, { output = it }, stringResource(R.string.pricing_output), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(cacheRead, { cacheRead = it }, stringResource(R.string.pricing_cache_read), Modifier.weight(1f))
                    NumberField(cacheWrite, { cacheWrite = it }, stringResource(R.string.pricing_cache_write), Modifier.weight(1f))
                }
                Text(
                    stringResource(R.string.pricing_per_million),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        PricingRate(
                            provider = provider.trim(),
                            model = model.trim(),
                            input = input.toDoubleOrNull() ?: 0.0,
                            output = output.toDoubleOrNull() ?: 0.0,
                            cacheRead = cacheRead.toDoubleOrNull(),
                            cacheWrite = cacheWrite.toDoubleOrNull(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * Account-wide Model API keys.
 *
 * Creation is the only moment the secret exists in readable form, so the
 * success path hands it straight to a copy dialog and never stores it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(state: UiState, vm: AppViewModel) {
    var editor by remember { mutableStateOf<ModelApiKey?>(null) }
    var adding by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<ModelApiKey?>(null) }
    val clipboard = LocalClipboardManager.current

    if (adding || editor != null) {
        ApiKeyDialog(
            initial = editor,
            catalog = state.modelApiCatalog,
            onDismiss = { adding = false; editor = null },
            onConfirm = { name, models, budget ->
                val target = editor
                if (target == null) vm.createApiKey(name, models, budget)
                else vm.editApiKey(target, name, models, budget)
                adding = false
                editor = null
            },
        )
    }

    state.createdApiKeySecret?.let { secret ->
        val copied = stringResource(R.string.action_copy)
        AlertDialog(
            onDismissRequest = vm::dismissCreatedApiKey,
            title = { Text(stringResource(R.string.apikeys_only_once)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(secret, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(secret)) }) { Text(copied) }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::dismissCreatedApiKey) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    revoking?.let { key ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.apikeys_revoke_confirm)) },
            text = { Text(key.name) },
            confirmButton = {
                TextButton(onClick = { vm.revokeApiKey(key); revoking = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apikeys_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::openApiKeys) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                stringResource(R.string.apikeys_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.modelApiKeys.isEmpty()) {
                Text(stringResource(R.string.apikeys_empty), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.modelApiKeys, key = { it.id }) { key ->
                    Card(
                        Modifier.fillMaxWidth().clickable { editor = key },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(key.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconButton(onClick = { revoking = key }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                }
                            }
                            Text(key.preview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${key.models.size} ${stringResource(R.string.apikeys_models)} · " +
                                    budgetLine(key, stringResource(R.string.apikeys_unlimited)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            budgetProgress(key)?.let { fraction ->
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                )
                            }
                            Text(
                                lastUsedLine(key).ifBlank { stringResource(R.string.apikeys_never_used) },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.apikeys_create), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

internal fun budgetLine(key: ModelApiKey, unlimited: String): String {
    val budget = key.budgetUsd
    val spent = "$${"%.2f".format(Locale.US, key.spentUsd)}"
    return if (budget == null) "$spent / $unlimited" else "$spent / $${"%.2f".format(Locale.US, budget)}"
}

/** Null when there is nothing meaningful to draw (no budget, or budget 0). */
internal fun budgetProgress(key: ModelApiKey): Float? {
    val budget = key.budgetUsd ?: return null
    if (budget <= 0.0) return 1f
    return (key.spentUsd / budget).toFloat().coerceIn(0f, 1f)
}

internal fun lastUsedLine(key: ModelApiKey): String {
    val at = key.lastUsedAt ?: return ""
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(at))
    return stamp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeyDialog(
    initial: ModelApiKey?,
    catalog: List<KeyModel>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>, Double?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var budget by remember { mutableStateOf(initial?.budgetUsd?.let(::trimNumber).orEmpty()) }
    var selected by remember { mutableStateOf(initial?.models.orEmpty().toSet()) }
    var picking by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && selected.isNotEmpty()

    if (picking) {
        ModelPickerDialog(
            catalog = catalog,
            selected = selected,
            onDismiss = { picking = false },
            onConfirm = { chosen -> selected = chosen; picking = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.apikeys_create else R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(
                    budget,
                    { raw -> budget = raw.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.apikeys_budget)) },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.apikeys_budget_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = "${selected.size} ${stringResource(R.string.apikeys_selected_count)}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.apikeys_models)) },
                    trailingIcon = {
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.apikeys_search_model)) }
                    },
                )
                if (selected.isEmpty()) {
                    Text(
                        stringResource(R.string.apikeys_pick_models),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(name.trim(), selected.toList(), budget.toDoubleOrNull()) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** 977 models on a live server: search first, then tap rows. */
@Composable
private fun ModelPickerDialog(
    catalog: List<KeyModel>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(selected) }
    val matches = remember(query, catalog) {
        if (query.isBlank()) catalog else catalog.filter { it.id.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${draft.size} ${stringResource(R.string.apikeys_selected_count)}") },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.apikeys_search_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(matches, key = { it.id }) { model ->
                        val checked = model.id in draft
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                draft = if (checked) draft - model.id else draft + model.id
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                model.id,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The rate row that applies to `model`, or null when the table has no opinion. */
internal fun matchRate(model: String, rates: List<PricingRate>): PricingRate? {
    if (model.isBlank()) return null
    return rates.firstOrNull { it.model == model }
        ?: rates.firstOrNull { model == "${it.provider}/${it.model}" }
        ?: rates.firstOrNull {
            it.provider.isNotBlank() && model.startsWith("${it.provider}/") && model.endsWith("/${it.model}")
        }
}

/**
 * USD for one conversation's token split.
 *
 * Cache reads are billed at their own rate when one is set, and at the input
 * rate otherwise — the same fallback the server applies. Anything unpriced
 * returns null so the sheet can say "unknown" instead of printing a confident
 * zero.
 */
internal fun estimateCost(usage: SessionUsage, rates: List<PricingRate>): Double? {
    val rate = matchRate(usage.model, rates) ?: return null
    val perMillion = 1_000_000.0
    val inputCost = usage.input * rate.input
    val outputCost = usage.output * rate.output
    val readCost = usage.cacheRead * (rate.cacheRead ?: rate.input)
    val writeCost = usage.cacheWrite * (rate.cacheWrite ?: rate.input)
    return (inputCost + outputCost + readCost + writeCost) / perMillion
}

@Composable
fun SessionUsageDialog(usage: SessionUsage, rates: List<PricingRate>, onDismiss: () -> Unit) {
    val cost = remember(usage, rates) { estimateCost(usage, rates) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_session_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                usageRow(stringResource(R.string.usage_input), usage.input)
                usageRow(stringResource(R.string.usage_output), usage.output)
                usageRow(stringResource(R.string.usage_cache_read), usage.cacheRead)
                if (usage.cacheWrite > 0) usageRow(stringResource(R.string.usage_cache_write), usage.cacheWrite)
                if (usage.reasoning > 0) usageRow(stringResource(R.string.usage_reasoning), usage.reasoning)
                usage.cacheHitRate?.let { rate ->
                    Text(
                        "${stringResource(R.string.usage_hit_rate)}  ${"%.0f".format(rate * 100)}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                Text(
                    if (cost == null) {
                        stringResource(R.string.usage_cost_unknown)
                    } else {
                        "${stringResource(R.string.usage_cost)}  $${"%.4f".format(Locale.US, cost)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cost == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (usage.model.isNotBlank()) {
                    Text(usage.model, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun usageRow(label: String, value: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            "%,d".format(value),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
