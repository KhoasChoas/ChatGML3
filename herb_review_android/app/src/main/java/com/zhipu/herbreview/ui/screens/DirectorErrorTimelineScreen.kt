package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.BuildConfig
import com.zhipu.herbreview.data.DirectorAnalyticsDemoData
import com.zhipu.herbreview.data.DirectorErrorTimelineRow
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.toErrorTimelineRow
import com.zhipu.herbreview.ui.integration.IntegrationOutcome
import com.zhipu.herbreview.ui.integration.IntegrationStatusPanel
import com.zhipu.herbreview.ui.integration.IntegrationStepLine
import com.zhipu.herbreview.ui.theme.HerbReviewTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DirectorErrorTimelineScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val apiConfigured = HerbReviewRemote.isConfigured()
    var remoteRows by remember { mutableStateOf<List<DirectorErrorTimelineRow>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var fetchOutcome by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) IntegrationOutcome.Waiting else IntegrationOutcome.Ok("内置演示 ${DirectorAnalyticsDemoData.errorTimeline.size} 条"),
        )
    }

    var draftRx by rememberSaveable { mutableStateOf("") }
    var draftReporter by rememberSaveable { mutableStateOf("") }
    var draftReviewer by rememberSaveable { mutableStateOf("") }
    var draftSession by rememberSaveable { mutableStateOf("") }
    var draftDate by rememberSaveable { mutableStateOf("30d") }
    var draftStatus by rememberSaveable { mutableStateOf("all") }

    var appliedRx by rememberSaveable { mutableStateOf("") }
    var appliedReporter by rememberSaveable { mutableStateOf("") }
    var appliedReviewer by rememberSaveable { mutableStateOf("") }
    var appliedSession by rememberSaveable { mutableStateOf("") }
    var appliedDate by rememberSaveable { mutableStateOf("30d") }
    var appliedStatus by rememberSaveable { mutableStateOf("all") }

    fun applyFilters() {
        appliedRx = draftRx.trim()
        appliedReporter = draftReporter.trim()
        appliedReviewer = draftReviewer.trim()
        appliedSession = draftSession.trim()
        appliedDate = draftDate
        appliedStatus = draftStatus
    }

    LaunchedEffect(BuildConfig.HERB_API_BASE_URL) {
        if (!apiConfigured) {
            remoteRows = null
            loadError = null
            fetchOutcome = IntegrationOutcome.Ok("内置演示 ${DirectorAnalyticsDemoData.errorTimeline.size} 条")
            return@LaunchedEffect
        }
        fetchOutcome = IntegrationOutcome.Working
        try {
            HerbReviewRemote.ensureLoggedIn()
            val list = HerbReviewRemote.fetchDirectorErrorTimeline(limit = 500, offset = 0).map { it.toErrorTimelineRow() }
            remoteRows = list
            loadError = null
            fetchOutcome = IntegrationOutcome.Ok("已加载 ${list.size} 条")
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            loadError = msg
            remoteRows = null
            fetchOutcome = IntegrationOutcome.Fail(msg)
        }
    }

    val allRows = remoteRows ?: DirectorAnalyticsDemoData.errorTimeline
    val now = LocalDateTime.now()
    val filtered = allRows.filter { row ->
        val byRx = appliedRx.isBlank() || row.prescriptionNo.contains(appliedRx, ignoreCase = true)
        val byRep = appliedReporter.isBlank() || row.reportedByName.contains(appliedReporter, ignoreCase = true)
        val byRev = appliedReviewer.isBlank() || (row.reviewerName ?: "").contains(appliedReviewer, ignoreCase = true)
        val bySess = appliedSession.isBlank() || (row.sessionId ?: "").contains(appliedSession, ignoreCase = true)
        val byDate = when (appliedDate) {
            "7d" -> row.reportedAt.toLocalDateTimeOrNull()?.isAfter(now.minusDays(7)) ?: true
            "30d" -> row.reportedAt.toLocalDateTimeOrNull()?.isAfter(now.minusDays(30)) ?: true
            else -> true
        }
        val byStatus = when (appliedStatus) {
            "pending" -> row.reportStatus == "open" || row.reportStatus == "notified"
            "resolved" -> row.reportStatus == "resolved"
            "withdrawn" -> row.reportStatus == "withdrawn"
            else -> true
        }
        byRx && byRep && byRev && bySess && byDate && byStatus
    }

    val nTotal = filtered.size
    val nPending = filtered.count { it.reportStatus == "open" || it.reportStatus == "notified" }
    val nResolved = filtered.count { it.reportStatus == "resolved" }
    val nConfirm = filtered.count { it.decision == "confirm_error" }
    val nAdjust = filtered.count { it.decision == "adjust_recognition" }

    val lines = listOf(
        IntegrationStepLine("GET /analytics/director/error-timeline", fetchOutcome),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("← 返回汇总") }
            Text("报错与复核时间线", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when {
                    !apiConfigured -> "视图 director_error_resolution_timeline；以下为演示数据。"
                    loadError != null -> "拉取失败：$loadError。已回退演示数据。"
                    else -> "实时数据（${BuildConfig.HERB_API_BASE_URL}）。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            IntegrationStatusPanel(
                title = "数据拉取自检",
                summary = "与复核会话汇总页拆分展示，便于单独筛选与统计。",
                steps = lines,
            )
        }

        item {
            Text(
                text = "筛选条件",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = draftRx,
                onValueChange = { draftRx = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("处方编号") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applyFilters() }),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draftReporter,
                    onValueChange = { draftReporter = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("上报人") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { applyFilters() }),
                )
                OutlinedTextField(
                    value = draftReviewer,
                    onValueChange = { draftReviewer = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("报错台处理人") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { applyFilters() }),
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = draftSession,
                onValueChange = { draftSession = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("会话 ID（片段）") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applyFilters() }),
            )
            Spacer(Modifier.height(8.dp))
            Text("上报时间", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draftDate == "7d", onClick = { draftDate = "7d" }, label = { Text("近 7 天") })
                FilterChip(selected = draftDate == "30d", onClick = { draftDate = "30d" }, label = { Text("近 30 天") })
                FilterChip(selected = draftDate == "all", onClick = { draftDate = "all" }, label = { Text("全部") })
            }
            Spacer(Modifier.height(6.dp))
            Text("工单状态", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = draftStatus == "all", onClick = { draftStatus = "all" }, label = { Text("全部") })
                FilterChip(selected = draftStatus == "pending", onClick = { draftStatus = "pending" }, label = { Text("待处理") })
                FilterChip(selected = draftStatus == "resolved", onClick = { draftStatus = "resolved" }, label = { Text("已处理") })
                FilterChip(selected = draftStatus == "withdrawn", onClick = { draftStatus = "withdrawn" }, label = { Text("已撤回") })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "修改条件后请点击「应用筛选」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        draftRx = ""
                        draftReporter = ""
                        draftReviewer = ""
                        draftSession = ""
                        draftDate = "30d"
                        draftStatus = "all"
                        appliedRx = ""
                        appliedReporter = ""
                        appliedReviewer = ""
                        appliedSession = ""
                        appliedDate = "30d"
                        appliedStatus = "all"
                    },
                    enabled = draftRx.isNotBlank() || draftReporter.isNotBlank() || draftReviewer.isNotBlank() ||
                        draftSession.isNotBlank() || draftDate != "30d" || draftStatus != "all" ||
                        appliedRx.isNotBlank() || appliedReporter.isNotBlank() || appliedReviewer.isNotBlank() ||
                        appliedSession.isNotBlank() || appliedDate != "30d" || appliedStatus != "all",
                ) { Text("清空") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { applyFilters() }) { Text("应用筛选") }
            }
        }

        item {
            Text("统计（基于当前筛选）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TimelineStatMini("工单数", nTotal.toString(), Modifier.weight(1f))
                    TimelineStatMini("待处理", nPending.toString(), Modifier.weight(1f))
                    TimelineStatMini("已处理", nResolved.toString(), Modifier.weight(1f))
                    TimelineStatMini("确认有误", nConfirm.toString(), Modifier.weight(1f))
                    TimelineStatMini("采纳药名", nAdjust.toString(), Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "列表条目：$nTotal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "无匹配记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered, key = { it.errorReportId }) { row ->
                DirectorErrorTimelineListCard(row)
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TimelineStatMini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DirectorErrorTimelineListCard(row: DirectorErrorTimelineRow) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timeline, contentDescription = null, modifier = Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("工单 #${row.errorReportId}", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "${row.prescriptionNo} · 步骤 ${row.stepIndex}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.sessionId?.takeIf { it.isNotBlank() }?.let { sid ->
                        val short = if (sid.length > 24) "${sid.take(8)}…${sid.takeLast(6)}" else sid
                        Text(
                            text = "会话：$short",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = { Text(timelineReportStatusLabel(row.reportStatus)) },
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
                    text = "复核：$it · ${row.reviewedAt ?: "—"} · ${timelineDecisionLabel(row.decision)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.reviewComment?.takeIf { it.isNotBlank() }?.let {
                Text(text = "备注：$it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun String?.toLocalDateTimeOrNull(): LocalDateTime? {
    if (this.isNullOrBlank()) return null
    return try {
        LocalDateTime.parse(this.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } catch (_: Exception) {
        null
    }
}

private fun timelineReportStatusLabel(status: String): String = when (status) {
    "open" -> "待处理"
    "notified" -> "已通知"
    "resolved" -> "已处理"
    "withdrawn" -> "已撤回"
    else -> status
}

private fun timelineDecisionLabel(decision: String?): String = when (decision) {
    "confirm_error" -> "确认有误"
    "reject_error" -> "驳回"
    "adjust_recognition" -> "采纳药名"
    else -> decision ?: "—"
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DirectorErrorTimelineScreenPreview() {
    HerbReviewTheme {
        DirectorErrorTimelineScreen(onBack = {})
    }
}
