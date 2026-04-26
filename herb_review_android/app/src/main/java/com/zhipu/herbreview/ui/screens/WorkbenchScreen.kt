package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.data.PrescriptionCacheRepository
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.PrescriptionListItemDto
import com.zhipu.herbreview.ui.theme.HerbReviewTheme
import kotlinx.coroutines.launch

@Composable
fun WorkbenchScreen(
    modifier: Modifier = Modifier,
    session: PharmacistSession,
    /** prescriptionId > 0: open that prescription on Review tab; -1: use API 预设处方流程 */
    onStartTodayReview: (prescriptionId: Int) -> Unit = {},
    onOpenHistorySession: (String) -> Unit = {},
    onStartReviewForPrescription: (Int) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val apiConfigured = HerbReviewRemote.isConfigured()
    var historyLoading by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var historyItems by remember { mutableStateOf(listOf<HistorySessionItem>()) }
    var historyVisible by remember { mutableStateOf(false) }
    var summaryLoading by remember { mutableStateOf(false) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf(WorkbenchSummary()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf(listOf<PrescriptionListItemDto>()) }
    var searchFromCache by remember { mutableStateOf(false) }
    /** Selected row in current search results; -1 = none (「开始今日复核」走预设处方). */
    var selectedSearchPrescriptionId by remember { mutableIntStateOf(-1) }
    /** History list filter by prescription_id; -1 = all. */
    var historyFilterPrescriptionId by remember { mutableIntStateOf(-1) }

    fun refreshHistory() {
        if (!apiConfigured) {
            historyError = "未配置 API（local.properties 缺少 herbApi.baseUrl）"
            historyItems = emptyList()
            historyVisible = true
            return
        }
        scope.launch {
            historyLoading = true
            historyError = null
            try {
                HerbReviewRemote.ensureLoggedIn()
                val rxFilter = historyFilterPrescriptionId.takeIf { it > 0 }
                historyItems = HerbReviewRemote.fetchRecentReviewSessions(
                    limit = if (rxFilter != null) 50 else 20,
                    prescriptionId = rxFilter,
                    mine = true,
                ).map {
                    HistorySessionItem(
                        id = it.id,
                        prescriptionId = it.prescriptionId,
                        prescriptionNo = it.prescriptionNo,
                        status = it.status,
                        createdAt = it.createdAt,
                        stepCount = it.steps.size,
                    )
                }
                historyVisible = true
            } catch (e: Exception) {
                historyError = e.message ?: e.toString()
                historyItems = emptyList()
                historyVisible = true
            } finally {
                historyLoading = false
            }
        }
    }

    fun refreshSummary() {
        if (!apiConfigured) {
            summaryError = "未配置 API"
            summary = WorkbenchSummary()
            return
        }
        scope.launch {
            summaryLoading = true
            summaryError = null
            try {
                HerbReviewRemote.ensureLoggedIn()
                val sessions = HerbReviewRemote.fetchRecentReviewSessions(200, mine = true)
                val inProgress = sessions.count { it.status == "in_progress" }
                val draft = sessions.count { it.status == "draft" }
                val completed = sessions.count { it.status == "completed" }
                summary = WorkbenchSummary(
                    pendingReview = inProgress + draft,
                    inProgress = inProgress,
                    completed = completed,
                )
            } catch (e: Exception) {
                summaryError = e.message ?: e.toString()
                summary = WorkbenchSummary()
            } finally {
                summaryLoading = false
            }
        }
    }

    LaunchedEffect(apiConfigured) {
        if (apiConfigured) refreshSummary()
    }

    fun runPrescriptionSearch() {
        if (!apiConfigured) {
            searchError = "未配置 API（local.properties 缺少 herbApi.baseUrl）"
            searchResults = emptyList()
            searchFromCache = false
            return
        }
        scope.launch {
            searchLoading = true
            searchError = null
            searchFromCache = false
            try {
                HerbReviewRemote.ensureLoggedIn()
                val page = HerbReviewRemote.searchPrescriptions(searchQuery)
                searchResults = page.items
                PrescriptionCacheRepository.upsertSearchResults(appContext, page)
            } catch (e: Exception) {
                searchError = e.message ?: e.toString()
                val offline = PrescriptionCacheRepository.searchOffline(appContext, searchQuery)
                if (offline.isNotEmpty()) {
                    searchResults = offline
                    searchFromCache = true
                } else {
                    searchResults = emptyList()
                }
            } finally {
                searchLoading = false
                selectedSearchPrescriptionId = -1
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("工作台", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "${session.displayName}（工号 ${session.employeeId}）",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row {
            AssistChip(
                onClick = {},
                label = { Text("当班中") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(
                title = "待复核",
                value = statValue(summary.pendingReview, summaryLoading, summaryError),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            StatCard(
                title = "进行中",
                value = statValue(summary.inProgress, summaryLoading, summaryError),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            StatCard(
                title = "已完成",
                value = statValue(summary.completed, summaryLoading, summaryError),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("搜索处方", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "按处方编号、患者姓名或诊断关键词检索；结果写入本地 Room 缓存，网络失败时可回退离线列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("如 365624 或患者名") },
                        prefix = {
                            Text(
                                text = "关键词",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        },
                    )
                    Button(
                        onClick = { runPrescriptionSearch() },
                        enabled = !searchLoading,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (searchLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("搜索")
                        }
                    }
                }
                if (searchFromCache) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "当前为离线缓存结果（接口不可用或请求失败）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (searchError != null && !searchFromCache) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请求：$searchError",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("共 ${searchResults.size} 条", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            PrescriptionSearchRow(
                                item = item,
                                selected = item.id == selectedSearchPrescriptionId,
                                onSelect = { selectedSearchPrescriptionId = item.id },
                                onQuickReview = { onStartReviewForPrescription(item.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
                if (searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            selectedSearchPrescriptionId > 0 -> {
                                val no = searchResults.firstOrNull { it.id == selectedSearchPrescriptionId }?.prescriptionNo
                                "已选处方：${no ?: "id $selectedSearchPrescriptionId"}（将按此进入复核）"
                            }
                            else -> "提示：先在上方列表中点选一条处方，再点「开始今日复核」；未选则使用预设处方。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val id = selectedSearchPrescriptionId.takeIf { it > 0 } ?: -1
                        onStartTodayReview(id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("开始今日复核")
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        historyVisible = true
                        refreshHistory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Insights, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("查看历史记录")
                }
                if (historyVisible) {
                    Spacer(Modifier.height(10.dp))
                    HistoryPanel(
                        loading = historyLoading,
                        error = historyError,
                        items = historyItems,
                        filterPrescriptionId = historyFilterPrescriptionId,
                        selectedSearchPrescriptionId = selectedSearchPrescriptionId,
                        onRefresh = { refreshHistory() },
                        onOpenSession = onOpenHistorySession,
                        onApplyFilterFromSelection = {
                            if (selectedSearchPrescriptionId > 0) {
                                historyFilterPrescriptionId = selectedSearchPrescriptionId
                                refreshHistory()
                            }
                        },
                        onClearFilter = {
                            historyFilterPrescriptionId = -1
                            refreshHistory()
                        },
                    )
                }
            }
        }
    }
}

private data class WorkbenchSummary(
    val pendingReview: Int = 0,
    val inProgress: Int = 0,
    val completed: Int = 0,
)

private fun statValue(value: Int, loading: Boolean, error: String?): String = when {
    loading -> "..."
    error != null -> "-"
    else -> value.toString()
}

@Composable
private fun PrescriptionSearchRow(
    item: PrescriptionListItemDto,
    selected: Boolean,
    onSelect: () -> Unit,
    onQuickReview: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.prescriptionNo, style = MaterialTheme.typography.titleSmall)
                val sub = listOfNotNull(item.patientName, item.diagnosis).joinToString(" · ").ifBlank { "（无患者/诊断）" }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "味数 ${item.herbKindCount?.toString() ?: "—"} · id ${item.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onQuickReview, shape = RoundedCornerShape(10.dp)) { Text("复核") }
        }
    }
}

private data class HistorySessionItem(
    val id: String,
    val prescriptionId: Int,
    val prescriptionNo: String?,
    val status: String,
    val createdAt: String,
    val stepCount: Int,
    val parentSessionId: String? = null,
    val issueStepIndexes: String? = null,
    val isSuspendedByError: Boolean = false,
    val isRecheckSession: Boolean = false,
)

@Composable
private fun HistoryRow(
    item: HistorySessionItem,
    onOpenSession: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "会话 ${item.id.take(8)}…  ·  ${statusLabel(item.status)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!item.parentSessionId.isNullOrBlank()) {
                Text(
                    text = "返工链路：来源 ${item.parentSessionId.take(8)}…  · 问题步骤 ${item.issueStepIndexes ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = buildString {
                    append("处方 ")
                    if (!item.prescriptionNo.isNullOrBlank()) {
                        append(item.prescriptionNo)
                        append(" · id ")
                    }
                    append(item.prescriptionId)
                    append(" · 步骤 ${item.stepCount} · ${item.createdAt}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onOpenSession(item.id) }) { Text("继续") }
    }
}

@Composable
private fun SuspendedQueuePanel(
    loading: Boolean,
    error: String?,
    items: List<HistorySessionItem>,
    onOpenSession: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "挂起会话队列",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh, enabled = !loading) { Text("刷新") }
            }
            Text(
                text = "仅显示待继续会话：报错挂起 / 打回返工（下次复核优先从这里进入）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在同步会话…", style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }
            if (error != null) {
                Text("同步失败：$error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (items.isEmpty()) {
                Text("当前没有挂起会话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.take(5).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            val type = when {
                                item.isSuspendedByError -> "报错挂起"
                                item.isRecheckSession -> "打回返工"
                                else -> "待继续"
                            }
                            Text(
                                "[$type] 处方 ${item.prescriptionNo ?: item.prescriptionId} · 会话 ${item.id.take(8)}…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "步骤 ${item.stepCount} · ${item.createdAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onOpenSession(item.id) }) { Text("继续") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    loading: Boolean,
    error: String?,
    items: List<HistorySessionItem>,
    filterPrescriptionId: Int,
    selectedSearchPrescriptionId: Int,
    onRefresh: () -> Unit,
    onOpenSession: (String) -> Unit,
    onApplyFilterFromSelection: () -> Unit,
    onClearFilter: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (filterPrescriptionId > 0) {
                        "历史记录（处方 id $filterPrescriptionId）"
                    } else {
                        "历史记录（最近）"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onApplyFilterFromSelection,
                    enabled = selectedSearchPrescriptionId > 0 && !loading,
                ) {
                    Text("按选中处方筛选")
                }
                TextButton(
                    onClick = onClearFilter,
                    enabled = filterPrescriptionId > 0 && !loading,
                ) {
                    Text("显示全部")
                }
            }
            when {
                loading -> {
                    Row {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("加载中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error != null -> Text("加载失败：$error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                items.isEmpty() -> Text("暂无历史会话。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp),
                    ) {
                        items.take(8).forEachIndexed { idx, it ->
                            if (idx > 0) Spacer(Modifier.height(6.dp))
                            HistoryRow(item = it, onOpenSession = onOpenSession)
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "已完成"
    "in_progress" -> "进行中"
    "draft" -> "草稿"
    "cancelled" -> "已取消"
    else -> status
}

private fun parseNoteValue(notes: String?, key: String): String? {
    if (notes.isNullOrBlank()) return null
    return try {
        val safeKey = Regex.escape(key)
        val m = Regex("""(?:^|[;,\s])$safeKey=([^;,\s]+)""").find(notes)
        m?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Workbench")
@Composable
private fun WorkbenchScreenPreview() {
    HerbReviewTheme {
        WorkbenchScreen(
            session = PharmacistSession(
                employeeId = "3109",
                displayName = "药师（3109）",
                isDepartmentDirector = false,
                canSubmitErrorReport = true,
            ),
        )
    }
}
