package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.data.PrescriptionCacheRepository
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.PrescriptionListItemDto
import com.zhipu.herbreview.network.ReviewSessionDto
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
    var suspendedItems by remember { mutableStateOf(listOf<HistorySessionItem>()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf(listOf<PrescriptionListItemDto>()) }
    var searchFromCache by remember { mutableStateOf(false) }
    /** Selected row in current search results; -1 = none (「开始今日复核」走预设处方). */
    var selectedSearchPrescriptionId by remember { mutableIntStateOf(-1) }
    /** History list filter by prescription_id; -1 = all. */
    var historyFilterPrescriptionId by remember { mutableIntStateOf(-1) }
    /** When true, history panel only shows items that match 挂起队列规则. */
    var historySuspendedOnly by rememberSaveable { mutableStateOf(false) }
    var prescriptionCardExpanded by rememberSaveable { mutableStateOf(false) }
    var suspendedCardExpanded by rememberSaveable { mutableStateOf(false) }

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
                ).mapNotNull { runCatching { it.toHistorySessionItem() }.getOrNull() }
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
            suspendedItems = emptyList()
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
                val mapped = sessions.mapNotNull { runCatching { it.toHistorySessionItem() }.getOrNull() }
                suspendedItems = mapped
                    .filter { item ->
                        val queuedByError = item.isSuspendedByError
                        val queuedRecheck = item.isRecheckSession && item.status == "in_progress"
                        queuedByError || queuedRecheck
                    }
                    .distinctBy { it.id }
            } catch (e: Exception) {
                summaryError = e.message ?: e.toString()
                summary = WorkbenchSummary()
                suspendedItems = emptyList()
            } finally {
                summaryLoading = false
            }
        }
    }

    LaunchedEffect(apiConfigured) {
        if (apiConfigured) refreshSummary()
    }

    val activityContext = LocalContext.current
    DisposableEffect(activityContext, apiConfigured) {
        if (!apiConfigured) {
            return@DisposableEffect onDispose { }
        }
        val activity = activityContext as? ComponentActivity ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSummary()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
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
        if (apiConfigured && !summaryLoading && summaryError == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "挂起队列（报错挂起 / 打回返工）：${suspendedItems.size} 条 · 从后台回到本页会自动刷新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (apiConfigured) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ExpandableWorkbenchCard(
                    title = "搜索处方",
                    collapsedSummary = when {
                        searchResults.isNotEmpty() -> "已加载 ${searchResults.size} 条，轻点展开"
                        else -> "轻点展开以搜索处方并开始复核"
                    },
                    expanded = prescriptionCardExpanded,
                    onToggle = {
                        if (prescriptionCardExpanded) {
                            prescriptionCardExpanded = false
                        } else {
                            prescriptionCardExpanded = true
                            suspendedCardExpanded = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (prescriptionCardExpanded) {
                                Modifier.weight(1f)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Column(Modifier.fillMaxSize()) {
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
                                    .then(
                                        if (historyVisible) {
                                            Modifier.heightIn(min = 80.dp, max = 220.dp)
                                        } else {
                                            Modifier.weight(1f)
                                        },
                                    ),
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
                        } else {
                            if (!historyVisible) {
                                Spacer(Modifier.weight(1f))
                            }
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
                            val historyDisplayed = remember(historyItems, historySuspendedOnly) {
                                if (historySuspendedOnly) {
                                    historyItems.filter { it.isHistorySuspendedMatch() }
                                } else {
                                    historyItems
                                }
                            }
                            HistoryPanel(
                                loading = historyLoading,
                                error = historyError,
                                items = historyDisplayed,
                                filterPrescriptionId = historyFilterPrescriptionId,
                                selectedSearchPrescriptionId = selectedSearchPrescriptionId,
                                suspendedOnly = historySuspendedOnly,
                                onSuspendedOnlyChange = { historySuspendedOnly = it },
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
                Spacer(Modifier.height(12.dp))
                ExpandableWorkbenchCard(
                    title = "挂起会话队列",
                    collapsedSummary = "待继续 ${suspendedItems.size} 条 · 轻点展开",
                    expanded = suspendedCardExpanded,
                    onToggle = {
                        if (suspendedCardExpanded) {
                            suspendedCardExpanded = false
                        } else {
                            suspendedCardExpanded = true
                            prescriptionCardExpanded = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (suspendedCardExpanded) {
                                Modifier.weight(1f)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    SuspendedQueuePanel(
                        loading = summaryLoading,
                        error = summaryError,
                        items = suspendedItems,
                        onOpenSession = onOpenHistorySession,
                        onSearchSync = { refreshSummary() },
                    )
                }
                if (!prescriptionCardExpanded && !suspendedCardExpanded) {
                    Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
            ExpandableWorkbenchCard(
                title = "搜索处方",
                collapsedSummary = when {
                    searchResults.isNotEmpty() -> "已加载 ${searchResults.size} 条，轻点展开"
                    else -> "轻点展开以搜索处方（未配置 API 时仅可查看界面）"
                },
                expanded = prescriptionCardExpanded,
                onToggle = { prescriptionCardExpanded = !prescriptionCardExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (prescriptionCardExpanded) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "按处方编号、患者姓名或诊断关键词检索；配置 herbApi.baseUrl 后可联网搜索。",
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
                    if (searchError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "请求：$searchError",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (!prescriptionCardExpanded) {
                Spacer(Modifier.weight(1f))
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
private fun ExpandableWorkbenchCard(
    title: String,
    collapsedSummary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.fillMaxHeight() else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxSize() else Modifier)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!expanded && collapsedSummary != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            collapsedSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    content()
                }
            }
        }
    }
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

@Composable
private fun SuspendedSessionRow(
    item: HistorySessionItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onQuickContinue: () -> Unit,
) {
    val type = when {
        item.isSuspendedByError -> "报错挂起"
        item.isRecheckSession -> "打回返工"
        else -> "待继续"
    }
    val idShort = if (item.id.length > 14) "${item.id.take(14)}…" else item.id
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
                Text(
                    "[$type] 处方 ${item.prescriptionNo ?: item.prescriptionId}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "会话 $idShort",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "步骤 ${item.stepCount} · ${item.createdAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onQuickContinue, shape = RoundedCornerShape(10.dp)) { Text("继续") }
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

private fun HistorySessionItem.suspendedQueueTypeLabel(): String =
    when {
        isSuspendedByError -> "报错挂起"
        isRecheckSession -> "打回返工"
        else -> "待继续"
    }

private fun HistorySessionItem.matchesSuspendedTypeFilter(filter: String): Boolean =
    when (filter) {
        "error" -> isSuspendedByError
        "recheck" -> isRecheckSession
        else -> true
    }

/** Keyword match for queue search (id, prescription, dates, parent session, type label, etc.). */
private fun HistorySessionItem.matchesSuspendedQueueSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val typeLabel = suspendedQueueTypeLabel()
    val fields = listOf(
        id,
        prescriptionNo.orEmpty(),
        parentSessionId.orEmpty(),
        issueStepIndexes.orEmpty(),
        status,
        createdAt,
        prescriptionId.toString(),
        stepCount.toString(),
        typeLabel,
    )
    return fields.any { it.contains(q, ignoreCase = true) }
}

@Composable
private fun HistoryRow(
    item: HistorySessionItem,
    onOpenSession: (String) -> Unit,
) {
    val idShort = if (item.id.length > 14) "${item.id.take(14)}…" else item.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "会话 $idShort  ·  ${statusLabel(item.status)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.parentSessionId.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                val pShort =
                    if (item.parentSessionId.length > 14) "${item.parentSessionId.take(14)}…" else item.parentSessionId
                Text(
                    text = "返工链路：来源 $pShort  · 问题步骤 ${item.issueStepIndexes ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(6.dp))
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onOpenSession(item.id) },
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Text("继续", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SuspendedQueuePanel(
    loading: Boolean,
    error: String?,
    items: List<HistorySessionItem>,
    onOpenSession: (String) -> Unit,
    onSearchSync: () -> Unit,
) {
    var queueQueryDraft by rememberSaveable { mutableStateOf("") }
    var queueQueryApplied by rememberSaveable { mutableStateOf("") }
    var queueTypeFilter by rememberSaveable { mutableStateOf("all") }
    var selectedSuspendedId by rememberSaveable { mutableStateOf("") }
    val filterChipScroll = rememberScrollState()
    val afterType = remember(items, queueTypeFilter) {
        items.filter { it.matchesSuspendedTypeFilter(queueTypeFilter) }
    }
    val filtered = remember(afterType, queueQueryApplied) {
        val q = queueQueryApplied.trim()
        if (q.isEmpty()) {
            afterType
        } else {
            afterType.filter { it.matchesSuspendedQueueSearch(q) }
        }
    }
    val shown = remember(filtered) { filtered.take(30) }
    val typeFilteredCount = afterType.size
    val hasActiveFilter = queueTypeFilter != "all" || queueQueryApplied.isNotBlank()
    val errCount = remember(items) { items.count { it.isSuspendedByError } }
    val recheckCount = remember(items) { items.count { it.isRecheckSession } }

    LaunchedEffect(selectedSuspendedId, filtered.map { it.id }.joinToString()) {
        if (selectedSuspendedId.isNotEmpty() && filtered.none { it.id == selectedSuspendedId }) {
            selectedSuspendedId = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "待继续：报错挂起 / 打回返工。点「搜索」将同步服务端数据并按关键词筛选；列表可滚动，最多展示 30 条。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
            loading && items.isEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在同步会话…", style = MaterialTheme.typography.bodySmall)
                }
            }
            error != null && items.isEmpty() -> {
                Text(
                    "同步失败：$error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            items.isEmpty() && !loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = queueQueryDraft,
                        onValueChange = { queueQueryDraft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !loading,
                        placeholder = { Text("会话 id、处方号或时间片段") },
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
                        onClick = {
                            queueQueryApplied = queueQueryDraft.trim()
                            selectedSuspendedId = ""
                            onSearchSync()
                        },
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("搜索")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前没有挂起会话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    if (error != null) {
                        Text(
                            "同步失败：$error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = queueQueryDraft,
                            onValueChange = { queueQueryDraft = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !loading,
                            placeholder = { Text("会话 id、处方号或时间片段") },
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
                            onClick = {
                                queueQueryApplied = queueQueryDraft.trim()
                                selectedSuspendedId = ""
                                onSearchSync()
                            },
                            enabled = !loading,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("搜索")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(filterChipScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = queueTypeFilter == "all",
                            onClick = {
                                queueTypeFilter = "all"
                                selectedSuspendedId = ""
                            },
                            label = { Text("全部 ${items.size}") },
                        )
                        FilterChip(
                            selected = queueTypeFilter == "error",
                            onClick = {
                                queueTypeFilter = "error"
                                selectedSuspendedId = ""
                            },
                            label = { Text("报错挂起 $errCount") },
                        )
                        FilterChip(
                            selected = queueTypeFilter == "recheck",
                            onClick = {
                                queueTypeFilter = "recheck"
                                selectedSuspendedId = ""
                            },
                            label = { Text("打回返工 $recheckCount") },
                        )
                    }
                    if (hasActiveFilter) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "当前列表：${filtered.size} 条（类型范围内共 $typeFilteredCount 条，队列总计 ${items.size}）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (filtered.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "无匹配项，请调整类型筛选或关键词后点「搜索」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text("共 ${filtered.size} 条", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            items(shown, key = { it.id }) { item ->
                                SuspendedSessionRow(
                                    item = item,
                                    selected = item.id == selectedSuspendedId,
                                    onSelect = { selectedSuspendedId = item.id },
                                    onQuickContinue = { onOpenSession(item.id) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                selectedSuspendedId.isNotEmpty() -> {
                                    val short =
                                        if (selectedSuspendedId.length > 10) {
                                            "${selectedSuspendedId.take(10)}…"
                                        } else {
                                            selectedSuspendedId
                                        }
                                    "已选会话：$short（将按此继续）"
                                }
                                else -> "提示：先点选一条挂起会话，再点「继续所选会话」；行内「继续」可快捷进入。"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onOpenSession(selectedSuspendedId) },
                            enabled = selectedSuspendedId.isNotEmpty() && !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("继续所选会话")
                        }
                    }
                }
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
    suspendedOnly: Boolean,
    onSuspendedOnlyChange: (Boolean) -> Unit,
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
                TextButton(
                    onClick = { onSuspendedOnlyChange(!suspendedOnly) },
                    enabled = !loading,
                ) {
                    Text(if (suspendedOnly) "显示全部会话" else "仅挂起/返工")
                }
            }
            if (suspendedOnly) {
                Text(
                    text = "当前为客户端筛选，仅显示与「挂起队列」规则一致的会话。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items, key = { it.id }) { item ->
                            HistoryRow(item = item, onOpenSession = onOpenSession)
                        }
                    }
                }
            }
        }
    }
}

private fun HistorySessionItem.isHistorySuspendedMatch(): Boolean {
    val queuedByError = isSuspendedByError
    val queuedRecheck = isRecheckSession && status == "in_progress"
    return queuedByError || queuedRecheck
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

private fun ReviewSessionDto.toHistorySessionItem(): HistorySessionItem {
    val parent = parseNoteValue(notes, "parent_session_id")
    val issues = parseNoteValue(notes, "issue_step_indexes")
    val susp = parseNoteValue(notes, "suspended_pending_error")
    val suspendedTag = susp == "1" || (susp?.equals("true", ignoreCase = true) == true)
    val notesBlob = notes.orEmpty()
    val suspendedLoose = notesBlob.contains("suspended_pending_error")
    val st = (status ?: "").ifBlank { "unknown" }
    return HistorySessionItem(
        id = id.ifBlank { "unknown" },
        prescriptionId = prescriptionId,
        prescriptionNo = prescriptionNo,
        status = st,
        createdAt = createdAt,
        stepCount = runCatching { steps.size }.getOrElse { 0 },
        parentSessionId = parent,
        issueStepIndexes = issues,
        isSuspendedByError = st == "draft" && (suspendedTag || suspendedLoose),
        isRecheckSession = !parent.isNullOrBlank(),
    )
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
