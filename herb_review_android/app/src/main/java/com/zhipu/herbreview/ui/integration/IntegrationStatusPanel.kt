package com.zhipu.herbreview.ui.integration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class IntegrationStepLine(
    val label: String,
    val outcome: IntegrationOutcome,
)

@Composable
fun IntegrationStatusPanel(
    title: String,
    summary: String?,
    steps: List<IntegrationStepLine>,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyCollapsed: Boolean = true,
) {
    val visibleSteps = steps.filter { it.outcome !is IntegrationOutcome.Hidden }
    if (visibleSteps.isEmpty()) return

    var expanded by rememberSaveable(title, initiallyCollapsed) { mutableStateOf(!initiallyCollapsed) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (collapsible && !expanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = integrationCompactSummary(visibleSteps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        summary?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (collapsible) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开")
                    }
                }
            }
            if (!collapsible || expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                visibleSteps.forEachIndexed { idx, line ->
                    if (idx > 0) Spacer(Modifier.height(6.dp))
                    IntegrationStepRow(line = line)
                }
            }
        }
    }
}

private fun integrationCompactSummary(lines: List<IntegrationStepLine>): String {
    val ok = lines.count { it.outcome is IntegrationOutcome.Ok }
    val fail = lines.count { it.outcome is IntegrationOutcome.Fail }
    val wait = lines.count {
        it.outcome == IntegrationOutcome.Waiting || it.outcome == IntegrationOutcome.Working
    }
    val base = "共 ${lines.size} 项 · 已通过 $ok"
    return when {
        fail > 0 -> "$base · $fail 项失败"
        wait > 0 -> "$base · $wait 项未完成"
        else -> "$base · 全部就绪"
    }
}

@Composable
private fun IntegrationStepRow(line: IntegrationStepLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutcomeGlyph(outcome = line.outcome)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = line.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = outcomeCaption(line.outcome),
                style = MaterialTheme.typography.bodySmall,
                color = when (line.outcome) {
                    is IntegrationOutcome.Fail -> MaterialTheme.colorScheme.error
                    is IntegrationOutcome.Ok -> MaterialTheme.colorScheme.primary
                    IntegrationOutcome.NotApplicable -> MaterialTheme.colorScheme.onSurfaceVariant
                    IntegrationOutcome.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
                    IntegrationOutcome.Working -> MaterialTheme.colorScheme.primary
                    IntegrationOutcome.Hidden -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OutcomeGlyph(outcome: IntegrationOutcome) {
    when (outcome) {
        is IntegrationOutcome.Ok -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = "成功",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        is IntegrationOutcome.Fail -> Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "失败",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        IntegrationOutcome.Working -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        IntegrationOutcome.Waiting -> Icon(
            imageVector = Icons.Outlined.HourglassEmpty,
            contentDescription = "等待",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        IntegrationOutcome.NotApplicable -> Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = "未使用",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        IntegrationOutcome.Hidden -> Icon(
            imageVector = Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun outcomeCaption(outcome: IntegrationOutcome): String = when (outcome) {
    is IntegrationOutcome.Ok -> outcome.detail
    is IntegrationOutcome.Fail -> outcome.message
    IntegrationOutcome.Hidden -> ""
    IntegrationOutcome.NotApplicable -> "未使用（当前为离线演示）"
    IntegrationOutcome.Waiting -> "等待执行…"
    IntegrationOutcome.Working -> "正在请求…"
}
