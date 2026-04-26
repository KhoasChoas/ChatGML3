package com.zhipu.herbreview.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.BuildConfig
import com.zhipu.herbreview.data.DirectorAnalyticsDemoData
import com.zhipu.herbreview.data.DirectorErrorTimelineRow
import com.zhipu.herbreview.data.DirectorSessionStepRow
import com.zhipu.herbreview.data.DirectorWorkOverviewRow
import com.zhipu.herbreview.ui.theme.HerbReviewTheme
import com.zhipu.herbreview.ui.integration.IntegrationOutcome
import com.zhipu.herbreview.ui.integration.IntegrationStatusPanel
import com.zhipu.herbreview.ui.integration.IntegrationStepLine
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.toErrorTimelineRow
import com.zhipu.herbreview.network.toDirectorStep
import com.zhipu.herbreview.network.toOverviewRow
import kotlinx.coroutines.launch

@Composable
fun DirectorAnalyticsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expandedSessionIds by remember { mutableStateOf(setOf<String>()) }
    val apiConfigured = HerbReviewRemote.isConfigured()
    var remoteOverview by remember { mutableStateOf<List<DirectorWorkOverviewRow>?>(null) }
    var remoteTimeline by remember { mutableStateOf<List<DirectorErrorTimelineRow>?>(null) }
    var overviewError by remember { mutableStateOf<String?>(null) }
    var timelineError by remember { mutableStateOf<String?>(null) }
    var stepsCache by remember { mutableStateOf(mapOf<String, List<DirectorSessionStepRow>>()) }
    var stepErrors by remember { mutableStateOf(mapOf<String, String>()) }
    var loadingSessionIds by remember { mutableStateOf(setOf<String>()) }
    var stepFetchOk by remember { mutableIntStateOf(0) }
    var stepFetchFail by remember { mutableIntStateOf(0) }

    var overviewFetch by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) {
                IntegrationOutcome.Waiting
            } else {
                IntegrationOutcome.Ok("内置演示 ${DirectorAnalyticsDemoData.workOverview.size} 条")
            },
        )
    }
    var timelineFetch by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) {
                IntegrationOutcome.Waiting
            } else {
                IntegrationOutcome.Ok("内置演示 ${DirectorAnalyticsDemoData.errorTimeline.size} 条")
            },
        )
    }

    LaunchedEffect(BuildConfig.HERB_API_BASE_URL) {
        if (!apiConfigured) {
            remoteOverview = null
            remoteTimeline = null
            overviewError = null
            timelineError = null
            overviewFetch = IntegrationOutcome.Ok("内置演示 ${DirectorAnalyticsDemoData.workOverview.size} 条")
            timelineFetch = IntegrationOutcome.Ok("内置演示 ${DirectorAnalyticsDemoData.errorTimeline.size} 条")
            return@LaunchedEffect
        }
        overviewFetch = IntegrationOutcome.Working
        timelineFetch = IntegrationOutcome.Working
        try {
            HerbReviewRemote.ensureLoggedIn()
            val list = HerbReviewRemote.fetchDirectorOverview().map { it.toOverviewRow() }
            val tl = HerbReviewRemote.fetchDirectorErrorTimeline().map { it.toErrorTimelineRow() }
            remoteOverview = list
            remoteTimeline = tl
            overviewError = null
            timelineError = null
            overviewFetch = IntegrationOutcome.Ok("已加载 ${list.size} 条（含登录校验）")
            timelineFetch = IntegrationOutcome.Ok("已加载 ${tl.size} 条")
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            overviewError = msg
            timelineError = msg
            remoteOverview = null
            remoteTimeline = null
            overviewFetch = IntegrationOutcome.Fail(msg)
            timelineFetch = IntegrationOutcome.Fail(msg)
        }
    }

    val displayRows = remoteOverview ?: DirectorAnalyticsDemoData.workOverview
    val displayTimeline = remoteTimeline ?: DirectorAnalyticsDemoData.errorTimeline

    val detailPullOutcome = when {
        !apiConfigured -> IntegrationOutcome.NotApplicable
        stepFetchOk == 0 && stepFetchFail == 0 -> IntegrationOutcome.Waiting
        stepFetchFail == 0 -> IntegrationOutcome.Ok("成功 ${stepFetchOk} 次 · 已缓存 ${stepsCache.size} 个会话")
        stepFetchOk == 0 -> IntegrationOutcome.Fail("失败 ${stepFetchFail} 次${stepErrors.values.firstOrNull()?.let { "：$it" } ?: ""}")
        else -> IntegrationOutcome.Ok("成功 $stepFetchOk · 失败 $stepFetchFail · 缓存 ${stepsCache.size} 个会话")
    }

    val directorIntegrationLines = listOf(
        IntegrationStepLine("① 汇总 GET /analytics/director/work-overview", overviewFetch),
        IntegrationStepLine("② 展开明细 GET /review-sessions/{id}", detailPullOutcome),
        IntegrationStepLine("③ 时间线 GET /analytics/director/error-timeline", timelineFetch),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("工作分析（科主任）", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when {
                    !apiConfigured ->
                        "以下为基于当前数据库导出的模拟复核流水（重新导入 Excel 后可运行 seed_director_demo.py 刷新）。点击会话卡片可展开药材级明细。"
                    overviewError != null ->
                        "已尝试从 ${BuildConfig.HERB_API_BASE_URL} 拉取 director_work_overview，失败：$overviewError。已回退到内置演示数据。"
                    else ->
                        "以下为接口 director_work_overview 的实时数据（${BuildConfig.HERB_API_BASE_URL}）。展开会话时会请求 GET /api/v1/review-sessions/{id} 填充步骤明细。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            IntegrationStatusPanel(
                title = "数据拉取自检",
                summary = "与复核页相同：绿色对勾＝成功；红色为接口错误说明。展开卡片时会单独请求步骤明细。",
                steps = directorIntegrationLines,
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            SectionTitle(
                title = "复核会话汇总",
                subtitle = "对应视图 director_work_overview",
                icon = { Icon(Icons.Outlined.AssignmentTurnedIn, contentDescription = null) },
            )
        }
        items(displayRows, key = { it.sessionId }) { row ->
            val expanded = expandedSessionIds.contains(row.sessionId)
            val steps = if (remoteOverview != null) {
                stepsCache[row.sessionId] ?: emptyList()
            } else {
                DirectorAnalyticsDemoData.stepsFor(row.sessionId)
            }
            SessionOverviewCard(
                row = row,
                expanded = expanded,
                steps = steps,
                stepsLoading = loadingSessionIds.contains(row.sessionId),
                stepsLoadError = stepErrors[row.sessionId],
                onToggle = {
                    val willExpand = !expandedSessionIds.contains(row.sessionId)
                    expandedSessionIds = expandedSessionIds.toMutableSet().apply {
                        if (contains(row.sessionId)) remove(row.sessionId) else add(row.sessionId)
                    }
                    if (willExpand && remoteOverview != null && !stepsCache.containsKey(row.sessionId)) {
                        scope.launch {
                            loadingSessionIds = loadingSessionIds + row.sessionId
                            try {
                                HerbReviewRemote.ensureLoggedIn()
                                val dto = HerbReviewRemote.fetchReviewSession(row.sessionId)
                                stepsCache = stepsCache + (
                                    row.sessionId to dto.steps.map { it.toDirectorStep(row.sessionId) }
                                )
                                stepErrors = stepErrors - row.sessionId
                                stepFetchOk++
                            } catch (e: Exception) {
                                val msg = e.message ?: e.toString()
                                stepsCache = stepsCache + (row.sessionId to emptyList())
                                stepErrors = stepErrors + (row.sessionId to msg)
                                stepFetchFail++
                            } finally {
                                loadingSessionIds = loadingSessionIds - row.sessionId
                            }
                        }
                    }
                },
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            SectionTitle(
                title = "报错与复核时间线",
                subtitle = "对应视图 director_error_resolution_timeline",
                icon = { Icon(Icons.Outlined.Timeline, contentDescription = null) },
            )
        }
        if (remoteOverview != null && timelineError != null) {
            item {
                Text(
                    text = "报错时间线接口拉取失败：$timelineError。已回退到内置演示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (displayTimeline.isEmpty()) {
            item {
                Text(
                    text = "暂无报错记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(displayTimeline, key = { it.errorReportId }) { row -> ErrorTimelineCard(row) }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionOverviewCard(
    row: DirectorWorkOverviewRow,
    expanded: Boolean,
    steps: List<DirectorSessionStepRow>,
    stepsLoading: Boolean,
    stepsLoadError: String?,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "处方 ${row.prescriptionNo}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(sessionStatusLabel(row.sessionStatus)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "发起人：${row.createdByName}（工号 ${row.createdByEmployeeId}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "开始时间：${row.sessionStartedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.diagnosis?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "诊断：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatMini("步骤", row.stepTotal.toString(), Modifier.weight(1f))
                StatMini("正确", row.stepCorrect.toString(), Modifier.weight(1f))
                StatMini("错误", row.stepIncorrect.toString(), Modifier.weight(1f))
                StatMini("待复核", row.stepNeedsReview.toString(), Modifier.weight(1f))
                StatMini("报错单", row.errorReportsFiled.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatMini("人工修正", row.stepManualFix.toString(), Modifier.weight(1f))
                StatMini("报错待决", row.errorReportsPending.toString(), Modifier.weight(1f))
                StatMini("报错已决", row.errorReportsResolved.toString(), Modifier.weight(1f))
                StatMini("打回次数", row.returnCount.toString(), Modifier.weight(1f))
                StatMini("复核医师", row.reviewingDoctor?.ifBlank { "—" } ?: "—", Modifier.weight(1f))
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "药材识别与人工复核明细",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (stepsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在加载步骤…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    stepsLoadError?.let { err ->
                        Text(
                            text = "步骤加载失败：$err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (steps.isEmpty() && !stepsLoading) {
                        Text(
                            text = "暂无步骤数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (steps.isNotEmpty()) {
                        StepTableHeader()
                        steps.sortedBy { it.stepIndex }.forEachIndexed { idx, step ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            }
                            StepTableRow(step)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "再次点击本条可收起",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTableHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("药材", modifier = Modifier.weight(0.26f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("识别结果", modifier = Modifier.weight(0.37f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("人工复核", modifier = Modifier.weight(0.37f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepTableRow(step: DirectorSessionStepRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${step.stepIndex}. ${step.herbName}",
            modifier = Modifier.weight(0.26f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
        )
        Text(
            text = recognitionCell(step),
            modifier = Modifier.weight(0.37f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
        )
        Text(
            text = manualReviewCell(step),
            modifier = Modifier.weight(0.37f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 5,
        )
    }
}

private fun recognitionCell(step: DirectorSessionStepRow): String {
    val base = step.llmRecognizedName ?: "—"
    return "$base（${matchStatusLabel(step.matchStatus)}）"
}

private fun manualReviewCell(step: DirectorSessionStepRow): String {
    if (step.reviewDecision != null) {
        val parts = mutableListOf<String>()
        parts.add(decisionLabel(step.reviewDecision))
        step.reviewerName?.let { parts.add("复核：$it") }
        step.reviewAgreedHerbName?.takeIf { it.isNotBlank() }?.let { parts.add("采纳 $it") }
        step.reviewComment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString("｜")
    }
    return when {
        step.matchStatus == "incorrect" && !step.hasErrorReport -> "待人工复核（未提交报错）"
        step.matchStatus == "incorrect" && step.hasErrorReport -> "待人工复核（已提交报错）"
        step.matchStatus == "needs_review" -> "待人工确认"
        step.matchStatus == "pending" -> "—"
        else -> "—"
    }
}

private fun matchStatusLabel(status: String): String = when (status) {
    "correct" -> "一致"
    "incorrect" -> "不符"
    "needs_review" -> "存疑"
    "pending" -> "待识别"
    else -> status
}

@Composable
private fun StatMini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorTimelineCard(row: DirectorErrorTimelineRow) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("工单 #${row.errorReportId}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${row.prescriptionNo} · 步骤 ${row.stepIndex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(reportStatusLabel(row.reportStatus)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "应付：${row.expectedHerbName} ｜ LLM：${row.llmRecognizedName ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "上报：${row.reportedByName} · ${row.reportedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.reviewerName?.let {
                Text(
                    text = "复核：$it · ${row.reviewedAt ?: "—"} · ${decisionLabel(row.decision)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.reviewComment?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "备注：$it",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun sessionStatusLabel(status: String): String = when (status) {
    "completed" -> "已完成"
    "in_progress" -> "进行中"
    "draft" -> "草稿"
    "cancelled" -> "已取消"
    else -> status
}

private fun reportStatusLabel(status: String): String = when (status) {
    "open" -> "待处理"
    "notified" -> "已通知"
    "resolved" -> "已处理"
    "withdrawn" -> "已撤回"
    else -> status
}

private fun decisionLabel(decision: String?): String = when (decision) {
    "confirm_error" -> "确认有误"
    "reject_error" -> "驳回"
    "adjust_recognition" -> "采纳药名"
    else -> decision ?: "—"
}

@Preview(showBackground = true, showSystemUi = true, name = "Director Analytics")
@Composable
private fun DirectorAnalyticsScreenPreview() {
    HerbReviewTheme {
        DirectorAnalyticsScreen(onBack = {})
    }
}
