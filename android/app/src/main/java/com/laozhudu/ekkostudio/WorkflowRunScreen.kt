package com.laozhudu.ekkostudio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One workflow run: what each node did, how long it took, and the graph it ran.
 *
 * The list of runs was already there, but a run is only useful if you can see
 * *which* node failed — that lives in `node_sessions`, which the old model threw
 * away. The graph is drawn read-only: arranging nodes is an editing job that
 * belongs on the desktop, and a phone that let you drag nodes into a mess would
 * be a step backwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunScreen(state: UiState, viewModel: AppViewModel) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val run = state.openWorkflowRun
    val workflow = state.workflows.firstOrNull { it.id == run?.workflowId }

    LaunchedEffect(run?.id) {
        if (run == null && state.openWorkflowRunId != null) viewModel.reloadWorkflowRun()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workflow?.name ?: stringResource(R.string.workflow_run_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = viewModel::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::reloadWorkflowRun) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            run ?: run {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.workflow_run_loading), fontSize = 12.sp, color = inkTextMuted(inkDark))
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusDot(run.status)
                Text(run.status, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        run.durationText(),
                        run.triggerSource.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    fontSize = 10.sp,
                    color = inkTextMuted(inkDark),
                )
                Box(Modifier.weight(1f))
                if (run.running) {
                    TextButton(onClick = { viewModel.stopWorkflowRun(workflowId = run.workflowId, runId = run.id) }) {
                        Text(stringResource(R.string.workflow_run_stop), color = inkError(inkDark))
                    }
                }
            }
            run.error?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = inkError(inkDark),
                )
            }

            // The graph, drawn from the workflow's own node positions.
            workflow?.let { flow ->
                WorkflowGraph(
                    nodesJson = flow.nodesJson,
                    edgesJson = flow.edgesJson,
                    statusForNode = { nodeId -> run.nodeSessions.firstOrNull { it.nodeId == nodeId }?.status },
                )
            }

            Text(
                stringResource(R.string.workflow_run_nodes, run.nodeSessions.size),
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(run.nodeSessions, key = { it.id }) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatusDot(session.status)
                        Column(Modifier.weight(1f)) {
                            Text(session.nodeId, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                listOfNotNull(
                                    session.status,
                                    session.durationText(),
                                    session.error?.take(80),
                                ).joinToString(" · "),
                                fontSize = 10.sp,
                                color = if (session.error != null) inkError(inkDark) else inkTextMuted(inkDark),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // A blocked node is the only one a phone can act on.
                        if (session.status == "blocked") {
                            TextButton(onClick = {
                                viewModel.approveWorkflowNode(
                                    workflowId = run.workflowId,
                                    runId = run.id,
                                    nodeId = session.nodeId,
                                    approved = true,
                                )
                            }) { Text(stringResource(R.string.workflow_run_approve)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val tint = when (status) {
        "completed", "success", "succeeded" -> inkSuccess(inkDark)
        "failed", "error", "canceled", "cancelled" -> inkError(inkDark)
        "blocked" -> inkWarning(inkDark)
        else -> inkInfo(inkDark)
    }
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(tint))
}

private fun StudioWorkflowRun.durationText(): String? =
    durationMillis?.let { formatDuration(it) }

private fun WorkflowNodeSession.durationText(): String? =
    durationMillis?.let { formatDuration(it) }

internal fun formatDuration(millis: Long): String = when {
    millis < 1_000 -> "${millis}ms"
    millis < 60_000 -> "${"%.1f".format(millis / 1000.0)}s"
    else -> "${millis / 60_000}m${(millis % 60_000) / 1000}s"
}

/**
 * A read-only picture of the workflow.
 *
 * Positions come from each node's `position` when the workflow has one; when it
 * does not (a workflow created through the API can omit them) the nodes are laid
 * out in a row rather than stacked on top of each other.
 */
@Composable
private fun WorkflowGraph(
    nodesJson: String,
    edgesJson: String,
    statusForNode: (String) -> String?,
) {
    val inkDark = MaterialTheme.colorScheme.isInkDark()
    val nodes = rememberNodes(nodesJson)
    val edges = rememberEdges(edgesJson)
    if (nodes.isEmpty()) return

    val coordinates = remember(nodes) { layoutWorkflowNodes(nodes) }
    val width = (coordinates.values.maxOfOrNull { it.first } ?: 0f) + GRAPH_NODE_W + 24f
    val height = (coordinates.values.maxOfOrNull { it.second } ?: 0f) + GRAPH_NODE_H + 24f

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Canvas(Modifier.size(width.dp, 220.dp)) {
                edges.forEach { (from, to) ->
                    val a = coordinates[from] ?: return@forEach
                    val b = coordinates[to] ?: return@forEach
                    drawLine(
                        color = inkBorder(inkDark),
                        start = Offset(a.first + GRAPH_NODE_W / 2, a.second + GRAPH_NODE_H),
                        end = Offset(b.first + GRAPH_NODE_W / 2, b.second),
                        strokeWidth = 2f,
                    )
                }
                coordinates.forEach { (id, position) ->
                    val status = statusForNode(id)
                    val tint = when (status) {
                        "completed", "success", "succeeded" -> inkSuccess(inkDark)
                        "failed", "error" -> inkError(inkDark)
                        "blocked" -> inkWarning(inkDark)
                        "running" -> inkInfo(inkDark)
                        else -> inkBorder(inkDark)
                    }
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(position.first, position.second),
                        size = androidx.compose.ui.geometry.Size(GRAPH_NODE_W, GRAPH_NODE_H),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        style = Stroke(width = 2f),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.workflow_graph_hint),
            fontSize = 9.sp,
            color = inkTextMuted(inkDark),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

internal const val GRAPH_NODE_W = 120f
internal const val GRAPH_NODE_H = 40f
private const val GRAPH_GAP_X = 40f
private const val GRAPH_GAP_Y = 80f
private const val GRAPH_PER_ROW = 3

internal data class GraphNode(val id: String, val x: Float?, val y: Float?)
internal data class GraphEdge(val from: String, val to: String)

@Composable
private fun rememberNodes(json: String): List<GraphNode> =
    remember(json) { parseGraphNodes(json) }

@Composable
private fun rememberEdges(json: String): List<GraphEdge> =
    remember(json) { parseGraphEdges(json) }

internal fun parseGraphNodes(json: String): List<GraphNode> {
    val array = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { node ->
            val id = node.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val position = node.optJSONObject("position")
            GraphNode(
                id = id,
                x = position?.optDouble("x")?.takeIf { !it.isNaN() }?.toFloat(),
                y = position?.optDouble("y")?.takeIf { !it.isNaN() }?.toFloat(),
            )
        }
    }
}

internal fun parseGraphEdges(json: String): List<GraphEdge> {
    val array = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { edge ->
            val from = edge.optString("source").ifBlank { edge.optString("from") }
            val to = edge.optString("target").ifBlank { edge.optString("to") }
            if (from.isBlank() || to.isBlank()) null else GraphEdge(from, to)
        }
    }
}

/**
 * Where to draw each node: its own position when it has one, otherwise a grid.
 *
 * Positions from the canvas can be negative or overlapping; a phone screen is a
 * different canvas, so they are normalised to start at the origin and spaced at
 * least one node apart.
 */
internal fun layoutWorkflowNodes(nodes: List<GraphNode>): Map<String, Pair<Float, Float>> {
    val withPositions = nodes.filter { it.x != null && it.y != null }
    if (withPositions.size == nodes.size && withPositions.isNotEmpty()) {
        val minX = withPositions.minOf { it.x!! }
        val minY = withPositions.minOf { it.y!! }
        return withPositions.associate { it.id to (it.x!! - minX) to (it.y!! - minY) }
    }
    return nodes.mapIndexed { index, node ->
        val column = index % GRAPH_PER_ROW
        val row = index / GRAPH_PER_ROW
        node.id to (column * (GRAPH_NODE_W + GRAPH_GAP_X)) to (row * (GRAPH_NODE_H + GRAPH_GAP_Y))
    }.toMap()
}
