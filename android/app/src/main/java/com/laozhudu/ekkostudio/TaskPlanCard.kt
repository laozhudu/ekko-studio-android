package com.laozhudu.ekkostudio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Completed / total steps of the plan being displayed. Pure, so it is testable. */
internal fun planProgress(steps: List<TaskPlanStep>): Pair<Int, Int> =
    steps.count { it.status == "completed" } to steps.size

/** The step the agent is on right now, or the next one that has not finished. */
internal fun planCurrentStep(steps: List<TaskPlanStep>): TaskPlanStep? =
    steps.firstOrNull { it.status == "in_progress" }
        ?: steps.firstOrNull { it.status != "completed" }

/**
 * Collapsed summary of the agent's task plan for this conversation.
 *
 * Layout follows the web's `.task-plan-card`: title at weight 600, the count on
 * the trailing edge, a muted execution-state line, and per-step markers where a
 * finished step is `--success` and the running one is `--accent-primary`.
 *
 * One deliberate addition: the running marker pulses. The web conveys "running"
 * with colour alone, which is easy to miss on a phone held at arm's length while
 * the agent works.
 */
@Composable
fun TaskPlanCard(plans: List<TaskPlanSnapshot>) {
    // Different plans restart their revision counter, so recency must come from
    // updated_at; sorting by revision picks whichever old plan happened to reach a
    // high number.
    val latest = plans.maxByOrNull { it.updatedAt } ?: return
    var expanded by rememberSaveable(latest.planId, latest.updatedAt) { mutableStateOf(false) }

    val (done, total) = planProgress(latest.steps)
    val current = planCurrentStep(latest.steps)
    val running = latest.executionState.equals("running", ignoreCase = true)
    val inkDark = MaterialTheme.colorScheme.isInkDark()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            // --msg-assistant-bg, the surface the web card sits on.
            containerColor = inkMessageBg(inkDark),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.task_plan_title),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = inkTextPrimary(inkDark),
                    )
                    if (running) PulsingDot(color = MaterialTheme.colorScheme.primary)
                }
                if (!expanded && current != null) {
                    Text(
                        text = current.step,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (current.status == "in_progress") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            inkTextSecondary(inkDark)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (running || done < total) {
                    Text(
                        // `.plan-state`: 12px, 70% opacity, under the title.
                        text = stringResource(
                            if (running) R.string.task_plan_state_running else R.string.task_plan_state_paused,
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = inkTextMuted(inkDark).copy(alpha = 0.7f),
                    )
                }
            }
            Text(
                // `.plan-count`: 12px, 75% opacity, trailing.
                text = "$done/$total",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = inkTextMuted(inkDark).copy(alpha = 0.75f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = inkTextMuted(inkDark),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                latest.steps.forEach { step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (step.status) {
                            "completed" -> Text(
                                "✓",
                                style = MaterialTheme.typography.bodyMedium,
                                // `.step-icon { color: var(--success) }`
                                color = inkSuccess(inkDark),
                            )
                            "in_progress" -> PulsingDot(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                            else -> Text(
                                "○",
                                style = MaterialTheme.typography.bodyMedium,
                                color = inkTextMuted(inkDark),
                            )
                        }
                        Text(
                            text = step.step,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (step.status) {
                                "pending" -> inkTextMuted(inkDark)
                                "completed" -> inkTextSecondary(inkDark)
                                else -> inkTextPrimary(inkDark)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Small breathing dot used for "this is happening now". */
@Composable
private fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "planPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "planPulseAlpha",
    )
    Box(
        modifier = modifier
            .size(6.dp)
            .scale(0.85f + pulse * 0.25f)
            .clip(CircleShape)
            .alpha(pulse)
            .background(color),
    )
}
