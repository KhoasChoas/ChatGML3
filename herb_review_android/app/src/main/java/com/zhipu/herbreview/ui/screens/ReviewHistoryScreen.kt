package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhipu.herbreview.BuildConfig
import com.zhipu.herbreview.data.local.ReviewCompletionEntity
import com.zhipu.herbreview.data.local.ReviewCompletionRepository
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.ReviewSessionDto
import kotlinx.coroutines.launch
import java.text.DateFormat

@Composable
fun ReviewHistoryScreen(
    modifier: Modifier = Modifier,
    session: PharmacistSession,
    onBack: () -> Unit,
    onOpenHistorySession: (String) -> Unit = {},
) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val localList by ReviewCompletionRepository.observeForEmployee(ctx, session.employeeId, 120)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var remoteList by remember { mutableStateOf<List<ReviewSessionDto>>(emptyList()) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var loadingRemote by remember { mutableStateOf(false) }

    var queryDraft by rememberSaveable { mutableStateOf("") }
    var queryApplied by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf("all") }
    var selectedSessionId by rememberSaveable { mutableStateOf("") }
    val chipScroll = rememberScrollState()

    fun refreshRemote() {
        if (!HerbReviewRemote.isConfigured()) {
            remoteList = emptyList()
            remoteError = null
            return
        }
        scope.launch {
            loadingRemote = true
            remoteError = null
            try {
                HerbReviewRemote.ensureLoggedIn()
                remoteList = HerbReviewRemote.fetchRecentReviewSessions(limit = 120, mine = true)
                    .filter { it.status == "completed" }
            } catch (e: Exception) {
                remoteError = e.message ?: e.toString()
            } finally {
                loadingRemote = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshRemote() }

    val merged = remember(remoteList, localList) { mergeReviewHistory(remoteList, localList) }

    val afterSource = remember(merged, sourceFilter) {
        when (sourceFilter) {
            "remote" -> merged.filter { it.sourceBadge.startsWith("服务器") }
            "local" -> merged.filter { it.sourceBadge.startsWith("本机") }
            else -> merged
        }
    }

    val filtered = remember(afterSource, queryApplied) {
        val q = queryApplied.trim()
        if (q.isEmpty()) {
            afterSource
        } else {
            afterSource.filter { it.matchesHistoryQuery(q) }
        }
    }

    val remoteCount = remember(merged) { merged.count { it.sourceBadge.startsWith("服务器") } }
    val localCount = remember(merged) { merged.count { it.sourceBadge.startsWith("本机") } }

    LaunchedEffect(selectedSessionId, filtered.map { it.sessionId }.joinToString()) {
        if (selectedSessionId.isNotEmpty() && filtered.none { it.sessionId == selectedSessionId }) {
            selectedSessionId = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text("复核历史", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "与本人账号相关的已归档会话（服务器 + 本机摘要）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!HerbReviewRemote.isConfigured()) {
            Text(
                text = "未配置 API：仅显示本机「完成并归档」留下的记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                text = "接口：GET /review-sessions?mine=true · ${BuildConfig.HERB_API_BASE_URL}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            remoteError?.let {
                Text("同步失败：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = queryDraft,
                onValueChange = { queryDraft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !loadingRemote,
                placeholder = { Text("会话 id、处方号、统计摘要…") },
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
                    queryApplied = queryDraft.trim()
                    selectedSessionId = ""
                    if (HerbReviewRemote.isConfigured()) {
                        refreshRemote()
                    }
                },
                enabled = !loadingRemote,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (loadingRemote) {
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
                .horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = sourceFilter == "all",
                onClick = {
                    sourceFilter = "all"
                    selectedSessionId = ""
                },
                label = { Text("全部 ${merged.size}") },
            )
            FilterChip(
                selected = sourceFilter == "remote",
                onClick = {
                    sourceFilter = "remote"
                    selectedSessionId = ""
                },
                label = { Text("服务器 $remoteCount") },
            )
            FilterChip(
                selected = sourceFilter == "local",
                onClick = {
                    sourceFilter = "local"
                    selectedSessionId = ""
                },
                label = { Text("本机 $localCount") },
            )
        }
        if (queryApplied.isNotBlank() || sourceFilter != "all") {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "当前列表：${filtered.size} 条（来源筛选后共 ${afterSource.size} 条，总计 ${merged.size}）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (loadingRemote && merged.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("加载服务器记录…", style = MaterialTheme.typography.bodySmall)
            }
        } else if (filtered.isEmpty()) {
            Text(
                text = if (merged.isEmpty()) {
                    "暂无记录。完成一次复核并点击「完成并归档」后，会出现在此列表。"
                } else {
                    "无匹配项，请调整关键词或来源筛选后点「搜索」。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("共 ${filtered.size} 条", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(filtered, key = { it.sessionId }) { row ->
                    HistoryMergedCard(
                        row = row,
                        selected = row.sessionId == selectedSessionId,
                        onSelect = { selectedSessionId = row.sessionId },
                        onQuickOpen = {
                            onOpenHistorySession(row.sessionId)
                            onBack()
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    selectedSessionId.isNotEmpty() -> {
                        val short =
                            if (selectedSessionId.length > 12) "${selectedSessionId.take(12)}…" else selectedSessionId
                        "已选会话：$short（将跳转复核页并加载该会话）"
                    }
                    else -> "提示：点选一条记录后，点下方主按钮进入复核；行内「打开」可快捷跳转。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    onOpenHistorySession(selectedSessionId)
                    onBack()
                },
                enabled = selectedSessionId.isNotEmpty() && !loadingRemote,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("在复核页打开所选会话")
            }
        }
    }
}

private data class MergedHistoryRow(
    val sessionId: String,
    val prescriptionNo: String,
    val prescriptionId: Int,
    val timeLine: String,
    val detail: String,
    val sourceBadge: String,
)

private fun MergedHistoryRow.matchesHistoryQuery(q: String): Boolean {
    val fields = listOf(
        sessionId,
        prescriptionNo,
        prescriptionId.toString(),
        detail,
        timeLine,
        sourceBadge,
    )
    return fields.any { it.contains(q, ignoreCase = true) }
}

private fun mergeReviewHistory(
    remote: List<ReviewSessionDto>,
    local: List<ReviewCompletionEntity>,
): List<MergedHistoryRow> {
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val out = mutableListOf<MergedHistoryRow>()
    val seen = mutableSetOf<String>()
    for (r in remote.sortedByDescending { it.updatedAt }) {
        val steps = r.steps
        val c = steps.count { it.matchStatus == "correct" }
        val ic = steps.count { it.matchStatus == "incorrect" }
        val nr = steps.count { it.matchStatus == "needs_review" }
        out.add(
            MergedHistoryRow(
                sessionId = r.id,
                prescriptionNo = r.prescriptionNo ?: "—",
                prescriptionId = r.prescriptionId,
                timeLine = r.updatedAt,
                detail = "步骤 ${steps.size} · 一致 $c · 不符 $ic · 存疑 $nr",
                sourceBadge = "服务器 · completed",
            ),
        )
        seen.add(r.id)
    }
    for (l in local.sortedByDescending { it.completedAtMillis }) {
        if (l.sessionId in seen) continue
        out.add(
            MergedHistoryRow(
                sessionId = l.sessionId,
                prescriptionNo = l.prescriptionNo,
                prescriptionId = l.prescriptionId,
                timeLine = df.format(l.completedAtMillis),
                detail = "步骤 ${l.stepTotal} · 一致 ${l.stepCorrect} · 不符 ${l.stepIncorrect} · 存疑 ${l.stepNeedsReview}",
                sourceBadge = "本机 · ${l.archiveStatus}",
            ),
        )
    }
    return out
}

@Composable
private fun HistoryMergedCard(
    row: MergedHistoryRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onQuickOpen: () -> Unit,
) {
    val idShort = if (row.sessionId.length > 14) "${row.sessionId.take(14)}…" else row.sessionId
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
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "处方 ${row.prescriptionNo} · id ${row.prescriptionId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "会话 $idShort · ${row.timeLine}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.sourceBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(
                onClick = onQuickOpen,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("打开")
            }
        }
    }
}
