package com.zhipu.herbreview.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val localList by ReviewCompletionRepository.observeForEmployee(ctx, session.employeeId, 120)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var remoteList by remember { mutableStateOf<List<ReviewSessionDto>>(emptyList()) }
    var remoteError by remember { mutableStateOf<String?>(null) }
    var loadingRemote by remember { mutableStateOf(false) }

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
            if (HerbReviewRemote.isConfigured()) {
                TextButton(
                    onClick = { refreshRemote() },
                    enabled = !loadingRemote,
                ) { Text("刷新") }
            }
        }
        Spacer(Modifier.height(8.dp))
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
        if (loadingRemote) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("加载服务器记录…", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (merged.isEmpty()) {
            Text(
                text = "暂无记录。完成一次复核并点击「完成并归档」后，会出现在此列表。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(merged, key = { it.sessionId }) { row ->
                    HistoryRowCard(row)
                    Spacer(Modifier.height(8.dp))
                }
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
private fun HistoryRowCard(row: MergedHistoryRow) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "处方 ${row.prescriptionNo} · id=${row.prescriptionId}",
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "会话 ${row.sessionId.take(12)}… · ${row.timeLine}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.sourceBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
