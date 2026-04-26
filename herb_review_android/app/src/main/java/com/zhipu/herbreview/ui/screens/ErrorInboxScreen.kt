package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.network.DirectorErrorTimelineDto
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.ui.theme.HerbReviewTheme
import kotlinx.coroutines.launch

private enum class InboxListFilter {
    All,
    PendingReview,
    Reviewed,
}

@Composable
fun ErrorInboxScreen(
    modifier: Modifier = Modifier,
    session: PharmacistSession,
) {
    val scope = rememberCoroutineScope()
    val apiConfigured = HerbReviewRemote.isConfigured()
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(listOf<DirectorErrorTimelineDto>()) }
    var listFilter by rememberSaveable { mutableStateOf(InboxListFilter.All.name) }
    var detailRow by remember { mutableStateOf<DirectorErrorTimelineDto?>(null) }

    fun currentFilter(): InboxListFilter =
        runCatching { InboxListFilter.valueOf(listFilter) }.getOrDefault(InboxListFilter.All)

    fun loadInbox() {
        if (!apiConfigured) {
            loadError = "未配置 API（local.properties 缺少 herbApi.baseUrl）"
            rows = emptyList()
            return
        }
        scope.launch {
            loading = true
            loadError = null
            try {
                HerbReviewRemote.ensureLoggedIn()
                val f = currentFilter()
                val pendingOnly = if (f == InboxListFilter.PendingReview) true else null
                val reviewedOnly = if (f == InboxListFilter.Reviewed) true else null
                rows = HerbReviewRemote.fetchErrorReportsInbox(
                    limit = 120,
                    offset = 0,
                    status = null,
                    pendingOnly = pendingOnly,
                    reviewedOnly = reviewedOnly,
                )
            } catch (e: Exception) {
                loadError = e.message ?: e.toString()
                rows = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(apiConfigured, listFilter) {
        if (apiConfigured) loadInbox()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("报错台", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text("工单 / 复核") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
            AssistChip(
                onClick = {},
                label = { Text("可提交报错：${session.canSubmitErrorReport}") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "列表来自服务端视图；待复核工单可在此填写复核结论并提交（写入 error_report_reviews，并将工单标为已结案）。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val chipScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = currentFilter() == InboxListFilter.All,
                onClick = { listFilter = InboxListFilter.All.name },
                label = { Text("全部") },
            )
            FilterChip(
                selected = currentFilter() == InboxListFilter.PendingReview,
                onClick = { listFilter = InboxListFilter.PendingReview.name },
                label = { Text("待复核") },
            )
            FilterChip(
                selected = currentFilter() == InboxListFilter.Reviewed,
                onClick = { listFilter = InboxListFilter.Reviewed.name },
                label = { Text("已复核") },
            )
        }
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("工单列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { loadInbox() },
                        enabled = !loading && apiConfigured,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("刷新")
                    }
                }
                Spacer(Modifier.height(6.dp))
                when {
                    !apiConfigured -> {
                        Text(loadError ?: "", color = MaterialTheme.colorScheme.error)
                    }
                    loading && rows.isEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("加载中…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    loadError != null -> Text("加载失败：$loadError", color = MaterialTheme.colorScheme.error)
                    rows.isEmpty() -> Text(
                        "暂无工单。若刚升级接口，请在服务器上执行 seed 中的视图重建（director_error_resolution_timeline 含 session_id、description）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                        ) {
                            items(rows, key = { it.errorReportId }) { row ->
                                ErrorInboxRow(
                                    row = row,
                                    onOpen = { detailRow = row },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detailRow?.let { row ->
        ErrorReportDetailDialog(
            row = row,
            session = session,
            onDismiss = { detailRow = null },
            onSubmitted = {
                detailRow = null
                loadInbox()
            },
        )
    }
}

@Composable
private fun ErrorInboxRow(
    row: DirectorErrorTimelineDto,
    onOpen: () -> Unit,
) {
    val open = row.reviewId == null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ER-${row.errorReportId}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            AssistChip(
                onClick = {},
                label = { Text(reportStatusChip(row.reportStatus, row.reviewId)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (open) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    labelColor = if (open) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "处方 ${row.prescriptionNo ?: "—"} · 步骤 ${row.stepIndex ?: "—"} · ${row.expectedHerbName ?: "—"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!row.description.isNullOrBlank()) {
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Text(
            "提交：${row.reportedByName ?: "—"} · ${row.reportedAt ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.reviewerName != null || row.decision != null) {
            Text(
                "复核：${row.reviewerName ?: "—"} · ${decisionLabel(row.decision)} · ${row.reviewedAt ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "点击查看详情${if (open) "并提交复核" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ErrorReportDetailDialog(
    row: DirectorErrorTimelineDto,
    session: PharmacistSession,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canSubmitReview = row.reviewId == null
    var decision by remember(row.errorReportId) { mutableStateOf("confirm_error") }
    var agreedHerb by remember(row.errorReportId) { mutableStateOf(row.expectedHerbName.orEmpty()) }
    var comment by remember(row.errorReportId) { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("工单 ER-${row.errorReportId}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("状态：${reportStatusChip(row.reportStatus, row.reviewId)}", style = MaterialTheme.typography.bodyMedium)
                if (!row.sessionId.isNullOrBlank()) {
                    Text("会话：${row.sessionId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "处方 ${row.prescriptionNo ?: "—"} · 步骤 ${row.stepIndex ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "应付：${row.expectedHerbName ?: "—"} · 识别：${row.llmRecognizedName ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!row.description.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("说明", style = MaterialTheme.typography.labelMedium)
                    Text(row.description, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!canSubmitReview) {
                    Spacer(Modifier.height(8.dp))
                    Text("复核结论（已记录）", style = MaterialTheme.typography.titleSmall)
                    Text(decisionLabel(row.decision), style = MaterialTheme.typography.bodyMedium)
                    if (!row.agreedHerbName.isNullOrBlank()) {
                        Text("采纳药名：${row.agreedHerbName}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!row.reviewComment.isNullOrBlank()) {
                        Text("备注：${row.reviewComment}", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("提交复核（当前：${session.displayName}）", style = MaterialTheme.typography.titleSmall)
                    Text("结论类型", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "confirm_error" to "确认差错",
                            "reject_error" to "驳回（非差错）",
                            "adjust_recognition" to "调整识别结果",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = decision == value,
                                onClick = { decision = value; submitError = null },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (decision == "adjust_recognition") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = agreedHerb,
                            onValueChange = { agreedHerb = it; submitError = null },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("采纳药材名") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it; submitError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注（可选）") },
                        minLines = 2,
                    )
                    if (submitError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(submitError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            if (canSubmitReview) {
                val valid = decision != "adjust_recognition" || agreedHerb.isNotBlank()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            if (!valid) {
                                submitError = "选择「调整识别结果」时请填写采纳药材名"
                                return@FilledTonalButton
                            }
                            scope.launch {
                                submitting = true
                                submitError = null
                                try {
                                    HerbReviewRemote.ensureLoggedIn()
                                    HerbReviewRemote.submitErrorReportReview(
                                        reportId = row.errorReportId,
                                        decision = decision,
                                        agreedHerbName = agreedHerb.takeIf { decision == "adjust_recognition" },
                                        comment = comment.takeIf { it.isNotBlank() },
                                    )
                                    onSubmitted()
                                } catch (e: Exception) {
                                    submitError = e.message ?: e.toString()
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                        enabled = !submitting && valid,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("提交复核")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) { Text("关闭") }
        },
    )
}

private fun reportStatusChip(status: String?, reviewId: Int?): String {
    if (reviewId != null) return "已复核"
    return when (status?.lowercase()) {
        "open", "pending" -> "待处理"
        "notified" -> "已通知"
        "resolved" -> "已结案"
        "withdrawn" -> "已撤回"
        null, "" -> "待处理"
        else -> status
    }
}

private fun decisionLabel(decision: String?): String = when (decision) {
    "confirm_error" -> "确认差错"
    "reject_error" -> "驳回"
    "adjust_recognition" -> "调整识别"
    else -> decision ?: "—"
}

@Preview(showBackground = true, showSystemUi = true, name = "Error Inbox")
@Composable
private fun ErrorInboxScreenPreview() {
    HerbReviewTheme {
        ErrorInboxScreen(
            session = PharmacistSession(
                employeeId = "3055",
                displayName = "药师（3055）",
                isDepartmentDirector = false,
                canSubmitErrorReport = true,
            ),
        )
    }
}
