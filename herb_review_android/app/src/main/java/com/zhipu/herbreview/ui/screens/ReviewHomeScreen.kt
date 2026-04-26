package com.zhipu.herbreview.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhipu.herbreview.BuildConfig
import com.zhipu.herbreview.data.ReviewFlowDemoData
import com.zhipu.herbreview.data.ReviewFlowStepDemo
import com.zhipu.herbreview.data.ReviewPresetPrescriptionDemo
import com.zhipu.herbreview.data.local.ReviewCompletionRepository
import com.zhipu.herbreview.model.PharmacistSession
import com.zhipu.herbreview.ui.theme.HerbReviewTheme
import com.zhipu.herbreview.ui.integration.IntegrationOutcome
import com.zhipu.herbreview.ui.integration.IntegrationStatusPanel
import com.zhipu.herbreview.ui.integration.IntegrationStepLine
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.toFlowSteps
import com.zhipu.herbreview.network.toPresetDemo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 复核主流程（原型）：
 * 1 选处方 → 2 顺序拍照识图 → 3 识别结果与人工复核表。
 * 预设案例数据来自 [ReviewFlowDemoData]（由 seed_director_demo.py 从 SQLite 导出）。
 */
@Composable
fun ReviewHomeScreen(
    modifier: Modifier = Modifier,
    session: PharmacistSession,
    resumeSessionId: String? = null,
    onResumeSessionConsumed: () -> Unit = {},
    /** From workbench search; <= 0 means none. */
    pendingStartPrescriptionId: Int = -1,
    onPendingStartPrescriptionConsumed: () -> Unit = {},
    onExitReview: () -> Unit = {},
) {
    var phase by rememberSaveable { mutableIntStateOf(1) }
    var capturedCount by rememberSaveable { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val apiConfigured = HerbReviewRemote.isConfigured()
    var apiBootDone by remember { mutableStateOf(!apiConfigured) }
    /** 仅「登录 + 拉处方」阶段失败；与创建会话错误分离，便于分步可视化 */
    var bootFailure by remember { mutableStateOf<String?>(null) }
    var remoteRx by remember { mutableStateOf<ReviewPresetPrescriptionDemo?>(null) }
    var remoteRxId by remember { mutableStateOf<Int?>(null) }
    var remoteSessionId by remember { mutableStateOf<String?>(null) }
    var remoteFlow by remember { mutableStateOf<List<ReviewFlowStepDemo>?>(null) }

    var stepLogin by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) IntegrationOutcome.Waiting else IntegrationOutcome.NotApplicable,
        )
    }
    var stepRx by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) IntegrationOutcome.Waiting else IntegrationOutcome.NotApplicable,
        )
    }
    var stepSession by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) IntegrationOutcome.Waiting else IntegrationOutcome.NotApplicable,
        )
    }
    var stepFinalize by remember {
        mutableStateOf<IntegrationOutcome>(
            if (apiConfigured) IntegrationOutcome.Waiting else IntegrationOutcome.NotApplicable,
        )
    }

    LaunchedEffect(BuildConfig.HERB_API_BASE_URL) {
        if (!apiConfigured) {
            apiBootDone = true
            bootFailure = null
            return@LaunchedEffect
        }
        bootFailure = null
        stepLogin = IntegrationOutcome.Working
        try {
            HerbReviewRemote.ensureLoggedIn()
            stepLogin = IntegrationOutcome.Ok("工号 ${BuildConfig.HERB_API_LOGIN_EMPLOYEE_ID}")
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            stepLogin = IntegrationOutcome.Fail(msg)
            stepRx = IntegrationOutcome.Fail("未执行（登录失败）")
            stepSession = IntegrationOutcome.Fail("未创建（登录失败）")
            stepFinalize = IntegrationOutcome.Fail("未完成（登录失败）")
            bootFailure = msg
            remoteRx = null
            remoteRxId = null
            apiBootDone = true
            return@LaunchedEffect
        }
        stepRx = IntegrationOutcome.Working
        try {
            val dto = HerbReviewRemote.fetchPresetPrescription()
            remoteRxId = dto.id
            remoteRx = dto.toPresetDemo()
            stepRx = IntegrationOutcome.Ok("处方 ${dto.prescriptionNo} · id=${dto.id} · ${dto.items.size} 味")
            bootFailure = null
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            stepRx = IntegrationOutcome.Fail(msg)
            stepSession = IntegrationOutcome.Fail("未创建（处方未加载）")
            stepFinalize = IntegrationOutcome.Fail("未完成（处方未加载）")
            bootFailure = msg
            remoteRx = null
            remoteRxId = null
        } finally {
            apiBootDone = true
        }
    }

    LaunchedEffect(resumeSessionId) {
        if (resumeSessionId.isNullOrBlank()) return@LaunchedEffect
        if (!apiConfigured) {
            stepSession = IntegrationOutcome.Fail("无法继续历史会话：未配置 API")
            onResumeSessionConsumed()
            return@LaunchedEffect
        }
        try {
            HerbReviewRemote.ensureLoggedIn()
            val s = HerbReviewRemote.fetchReviewSession(resumeSessionId)
            val rx = HerbReviewRemote.fetchPrescriptionById(s.prescriptionId)
            remoteRxId = s.prescriptionId
            remoteRx = rx.toPresetDemo()
            remoteSessionId = s.id
            remoteFlow = s.toFlowSteps()
            val flowList = remoteFlow ?: emptyList()
            val isRecheckSession = s.notes.orEmpty().contains("parent_session_id=")
            stepSession = IntegrationOutcome.Ok(
                buildString {
                    append("已加载历史会话 ${s.id.take(8)}…")
                    if (isRecheckSession) append("（返工：从顺序拍照继续）")
                },
            )
            stepFinalize = when (s.status) {
                "completed" -> IntegrationOutcome.Ok("历史会话状态=completed")
                else -> IntegrationOutcome.Waiting
            }
            if (isRecheckSession) {
                capturedCount = flowList.count { it.matchStatus == "correct" }
                phase = 2
            } else {
                capturedCount = flowList.count { it.matchStatus != "pending" }
                phase = 3
            }
        } catch (e: Exception) {
            stepSession = IntegrationOutcome.Fail("加载历史会话失败：${e.message ?: e}")
        } finally {
            onResumeSessionConsumed()
        }
    }

    LaunchedEffect(pendingStartPrescriptionId, apiBootDone, apiConfigured) {
        if (pendingStartPrescriptionId <= 0) return@LaunchedEffect
        if (!apiConfigured) {
            onPendingStartPrescriptionConsumed()
            return@LaunchedEffect
        }
        if (!apiBootDone) return@LaunchedEffect
        try {
            HerbReviewRemote.ensureLoggedIn()
            val dto = HerbReviewRemote.fetchPrescriptionById(pendingStartPrescriptionId)
            remoteRxId = dto.id
            remoteRx = dto.toPresetDemo()
            remoteSessionId = null
            remoteFlow = null
            phase = 1
            capturedCount = 0
            bootFailure = null
            stepRx = IntegrationOutcome.Ok("工作台搜索 · 处方 ${dto.prescriptionNo} · id=${dto.id} · ${dto.items.size} 味")
            stepSession = IntegrationOutcome.Waiting
            stepFinalize = IntegrationOutcome.Waiting
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            stepRx = IntegrationOutcome.Fail("加载搜索处方失败：$msg")
        } finally {
            onPendingStartPrescriptionConsumed()
        }
    }

    val useRemoteRx = remoteRx != null && bootFailure == null
    val rx = if (useRemoteRx) remoteRx!! else ReviewFlowDemoData.presetPrescription
    val demoFlow = ReviewFlowDemoData.simulatedRecognitionFlow
    val flow = remoteFlow ?: demoFlow
    val lines = rx.lines
    val captureHerbNames = if (remoteFlow.isNullOrEmpty()) {
        lines.map { it.herbName }
    } else {
        remoteFlow!!.sortedBy { it.stepIndex }.map { it.herbName }
    }
    val captureTotal = captureHerbNames.size

    val sourceDescription = when {
        !apiConfigured -> ReviewFlowDemoData.DEMO_SOURCE_TAG
        !apiBootDone -> "正在连接 ${BuildConfig.HERB_API_BASE_URL} …"
        bootFailure != null -> "接口不可用，已回退演示数据：$bootFailure"
        else -> {
            val idPart = remoteRxId?.let { " · id=$it" }.orEmpty()
            "API 处方 · ${BuildConfig.HERB_API_BASE_URL}（${rx.prescriptionNo}$idPart）"
        }
    }

    val integrationLines = buildList {
        add(
            IntegrationStepLine(
                label = "① 数据模式",
                outcome = if (!apiConfigured) {
                    IntegrationOutcome.Ok("离线演示（未配置 herbApi.baseUrl）")
                } else {
                    IntegrationOutcome.Ok("远程 API · ${BuildConfig.HERB_API_BASE_URL}")
                },
            ),
        )
        add(IntegrationStepLine("② 登录 POST /auth/login", outcome = stepLogin))
        add(IntegrationStepLine("③ 拉取处方（列表 + 详情）", outcome = stepRx))
        add(IntegrationStepLine("④ 创建复核会话 POST /review-sessions", outcome = stepSession))
        add(IntegrationStepLine("⑤ 完成会话 PATCH /review-sessions/{id}/status", outcome = stepFinalize))
    }

    val rootScroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rootScroll),
    ) {
        Text("复核（顺序识图 + ChatGLM3）", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        IntegrationStatusPanel(
            title = "连接与接口自检",
            summary = "绿灯图标＝该步已成功；红字＝失败说明；沙漏＝尚未执行。",
            steps = integrationLines,
            collapsible = apiConfigured,
            initiallyCollapsed = true,
        )
        Spacer(Modifier.height(10.dp))
        if (apiConfigured && !apiBootDone) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在拉取处方…", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text("模型：ChatGLM3") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
            AssistChip(
                onClick = {},
                label = { Text("案例处方 ${rx.prescriptionNo}") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "流程：先选定处方 → 按清单顺序拍照 → LLM 识别与结果表。第 2 步已含流式识别演示卡片，后续可替换为真实 ChatGLM3 SSE。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ReviewPhaseStepIndicator(phase = phase)
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("当前用户：${session.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when (phase) {
                    1 -> PhaseSelectPrescription(
                        modifier = Modifier.fillMaxWidth(),
                        rx = rx,
                        sourceDescription = sourceDescription,
                        confirmEnabled = !apiConfigured || apiBootDone,
                        onConfirm = {
                            if (apiConfigured && remoteRxId != null && bootFailure == null) {
                                scope.launch {
                                    stepSession = IntegrationOutcome.Working
                                    try {
                                        HerbReviewRemote.ensureLoggedIn()
                                        val created = HerbReviewRemote.createReviewSession(remoteRxId!!)
                                        remoteSessionId = created.id
                                        remoteFlow = created.toFlowSteps()
                                        stepFinalize = IntegrationOutcome.Waiting
                                        stepSession = IntegrationOutcome.Ok(
                                            "会话 id=${created.id} · 步骤 ${created.steps.size} 条",
                                        )
                                        phase = 2
                                        capturedCount = 0
                                    } catch (e: Exception) {
                                        val msg = e.message ?: e.toString()
                                        stepSession = IntegrationOutcome.Fail(msg)
                                    }
                                }
                            } else {
                                if (apiConfigured) {
                                    stepSession = if (remoteRxId != null) {
                                        IntegrationOutcome.NotApplicable
                                    } else {
                                        IntegrationOutcome.Fail("处方未从接口加载，未调用创建会话")
                                    }
                                } else {
                                    stepSession = IntegrationOutcome.NotApplicable
                                }
                                phase = 2
                                capturedCount = 0
                            }
                        },
                    )
                    2 -> PhaseCapture(
                        herbNames = captureHerbNames,
                        capturedCount = capturedCount,
                        apiConfigured = apiConfigured,
                        sessionId = remoteSessionId,
                        orderedRemoteFlow = remoteFlow,
                        canSubmitErrorReport = session.canSubmitErrorReport,
                        onCaptureOne = {
                            if (capturedCount < captureTotal) {
                                if (apiConfigured && remoteSessionId != null && remoteFlow != null) {
                                    val currentIdx = capturedCount + 1
                                    val expected = captureHerbNames.getOrNull(capturedCount)
                                    val demoStep = demoFlow.firstOrNull { it.stepIndex == currentIdx }
                                    scope.launch {
                                        try {
                                            HerbReviewRemote.ensureLoggedIn()
                                            val full = HerbReviewRemote.fetchReviewSession(remoteSessionId!!)
                                            val target = full.steps.firstOrNull { it.stepIndex == currentIdx }
                                            if (target != null) {
                                                HerbReviewRemote.patchReviewStep(
                                                    sessionId = remoteSessionId!!,
                                                    stepId = target.id,
                                                    recognizedName = demoStep?.llmRecognizedName ?: expected,
                                                    matchStatus = demoStep?.matchStatus ?: "correct",
                                                )
                                                val refreshed = HerbReviewRemote.fetchReviewSession(remoteSessionId!!)
                                                remoteFlow = refreshed.toFlowSteps()
                                            }
                                        } catch (_: Exception) {
                                            // 保底：即使接口写入失败，演示流程也继续。
                                        } finally {
                                            if (capturedCount < captureTotal) capturedCount += 1
                                        }
                                    }
                                } else {
                                    if (capturedCount < captureTotal) capturedCount += 1
                                }
                            }
                        },
                        onUndoOne = {
                            if (capturedCount > 0) {
                                if (apiConfigured && remoteSessionId != null && remoteFlow != null) {
                                    val undoIdx = capturedCount
                                    scope.launch {
                                        try {
                                            HerbReviewRemote.ensureLoggedIn()
                                            val full = HerbReviewRemote.fetchReviewSession(remoteSessionId!!)
                                            val target = full.steps.firstOrNull { it.stepIndex == undoIdx }
                                            if (target != null) {
                                                HerbReviewRemote.patchReviewStep(
                                                    sessionId = remoteSessionId!!,
                                                    stepId = target.id,
                                                    recognizedName = null,
                                                    matchStatus = "pending",
                                                )
                                                val refreshed = HerbReviewRemote.fetchReviewSession(remoteSessionId!!)
                                                remoteFlow = refreshed.toFlowSteps()
                                            }
                                        } catch (_: Exception) {
                                            // 回退失败时仍允许本地回退，避免流程卡住。
                                        } finally {
                                            if (capturedCount > 0) capturedCount -= 1
                                        }
                                    }
                                } else {
                                    capturedCount -= 1
                                }
                            }
                        },
                        onSkipToResult = {
                            capturedCount = captureTotal
                            if (apiConfigured && remoteSessionId != null) {
                                scope.launch {
                                    stepFinalize = IntegrationOutcome.Working
                                    try {
                                        HerbReviewRemote.ensureLoggedIn()
                                        val fin = HerbReviewRemote.completeReviewSession(remoteSessionId!!)
                                        stepFinalize = IntegrationOutcome.Ok("会话 ${fin.id} 状态=${fin.status}")
                                    } catch (e: Exception) {
                                        stepFinalize = IntegrationOutcome.Fail(e.message ?: e.toString())
                                    } finally {
                                        phase = 3
                                    }
                                }
                            } else {
                                if (apiConfigured) {
                                    stepFinalize = IntegrationOutcome.NotApplicable
                                }
                                phase = 3
                            }
                        },
                        onNext = {
                            if (capturedCount >= captureTotal) {
                                if (apiConfigured && remoteSessionId != null) {
                                    scope.launch {
                                        stepFinalize = IntegrationOutcome.Working
                                        try {
                                            HerbReviewRemote.ensureLoggedIn()
                                            val fin = HerbReviewRemote.completeReviewSession(remoteSessionId!!)
                                            stepFinalize = IntegrationOutcome.Ok("会话 ${fin.id} 状态=${fin.status}")
                                        } catch (e: Exception) {
                                            stepFinalize = IntegrationOutcome.Fail(e.message ?: e.toString())
                                        } finally {
                                            phase = 3
                                        }
                                    }
                                } else {
                                    if (apiConfigured) {
                                        stepFinalize = IntegrationOutcome.NotApplicable
                                    }
                                    phase = 3
                                }
                            }
                        },
                    )
                    else -> PhaseResultTable(
                        flow = flow,
                        sessionId = remoteSessionId,
                        apiConfigured = apiConfigured,
                        canSubmitErrorReport = session.canSubmitErrorReport,
                        finalizeOutcome = stepFinalize,
                        onReturnForRecheck = {
                            if (!apiConfigured || remoteSessionId == null) {
                                stepFinalize = IntegrationOutcome.Fail("打回失败：当前无可打回的远程会话")
                            } else {
                                scope.launch {
                                    try {
                                        HerbReviewRemote.ensureLoggedIn()
                                        val returned = HerbReviewRemote.returnSessionForRecheck(remoteSessionId!!)
                                        stepSession = IntegrationOutcome.Ok(
                                            "已打回并挂起，已生成返工会话 ${returned.id.take(8)}…（${returned.steps.size} 条问题步骤）",
                                        )
                                        stepFinalize = IntegrationOutcome.Waiting
                                        phase = 1
                                        capturedCount = 0
                                        remoteFlow = null
                                        remoteSessionId = null
                                        onExitReview()
                                    } catch (e: Exception) {
                                        stepFinalize = IntegrationOutcome.Fail("打回失败：${e.message ?: e}")
                                    }
                                }
                            }
                        },
                        onFinishAndArchive = {
                            scope.launch {
                                val flowSteps = remoteFlow ?: demoFlow
                                val orderedSteps = flowSteps.sortedBy { it.stepIndex }
                                var archiveStatus = "offline_completed"
                                if (apiConfigured && remoteSessionId != null) {
                                    try {
                                        HerbReviewRemote.ensureLoggedIn()
                                        val cur = HerbReviewRemote.fetchReviewSession(remoteSessionId!!)
                                        if (cur.status != "completed") {
                                            val fin = HerbReviewRemote.completeReviewSession(remoteSessionId!!)
                                            stepFinalize = IntegrationOutcome.Ok(
                                                "会话 ${fin.id.take(8)}… 状态=${fin.status}",
                                            )
                                            archiveStatus = "completed"
                                        } else {
                                            stepFinalize = IntegrationOutcome.Ok("会话已是 completed")
                                            archiveStatus = "completed"
                                        }
                                    } catch (e: Exception) {
                                        stepFinalize = IntegrationOutcome.Fail(e.message ?: e.toString())
                                        archiveStatus = "archive_failed"
                                    }
                                } else if (apiConfigured) {
                                    stepFinalize = IntegrationOutcome.NotApplicable
                                }
                                val sid = remoteSessionId
                                    ?: "offline-${rx.prescriptionNo}-${System.currentTimeMillis()}"
                                val pid = remoteRxId ?: -1
                                ReviewCompletionRepository.recordCompleted(
                                    context = appContext,
                                    employeeId = session.employeeId,
                                    sessionId = sid,
                                    prescriptionNo = rx.prescriptionNo,
                                    prescriptionId = pid,
                                    archiveStatus = archiveStatus,
                                    stepTotal = orderedSteps.size,
                                    stepCorrect = orderedSteps.count { it.matchStatus == "correct" },
                                    stepIncorrect = orderedSteps.count { it.matchStatus == "incorrect" },
                                    stepNeedsReview = orderedSteps.count { it.matchStatus == "needs_review" },
                                )
                                phase = 1
                                capturedCount = 0
                                remoteFlow = null
                                remoteSessionId = null
                                if (apiConfigured) {
                                    stepSession = IntegrationOutcome.Waiting
                                    stepFinalize = IntegrationOutcome.Waiting
                                }
                                onExitReview()
                            }
                        },
                        onStartNewReview = {
                            phase = 1
                            capturedCount = 0
                            remoteFlow = null
                            remoteSessionId = null
                            if (apiConfigured) {
                                stepSession = IntegrationOutcome.Waiting
                                stepFinalize = IntegrationOutcome.Waiting
                            }
                        },
                        onExitWorkbench = {
                            phase = 1
                            capturedCount = 0
                            remoteFlow = null
                            remoteSessionId = null
                            if (apiConfigured) {
                                stepSession = IntegrationOutcome.Waiting
                                stepFinalize = IntegrationOutcome.Waiting
                            }
                            onExitReview()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewPhaseStepIndicator(phase: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReviewStepPill(
            stepIndex = 1,
            title = "选处方",
            icon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
            state = stepState(phase, stepNumber = 1),
            modifier = Modifier.weight(1f),
        )
        ReviewStepPill(
            stepIndex = 2,
            title = "拍照识图",
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
            state = stepState(phase, stepNumber = 2),
            modifier = Modifier.weight(1f),
        )
        ReviewStepPill(
            stepIndex = 3,
            title = "结果表",
            icon = { Icon(Icons.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp)) },
            state = stepState(phase, stepNumber = 3),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun stepState(phase: Int, stepNumber: Int) = when {
    phase > stepNumber -> ReviewStepState.Done
    phase == stepNumber -> ReviewStepState.Active
    else -> ReviewStepState.Pending
}

private enum class ReviewStepState { Done, Active, Pending }

@Composable
private fun ReviewStepPill(
    stepIndex: Int,
    title: String,
    icon: @Composable () -> Unit,
    state: ReviewStepState,
    modifier: Modifier = Modifier,
) {
    val colors = when (state) {
        ReviewStepState.Done -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        ReviewStepState.Active -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        )
        ReviewStepState.Pending -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    val onC = when (state) {
        ReviewStepState.Done -> MaterialTheme.colorScheme.onPrimaryContainer
        ReviewStepState.Active -> MaterialTheme.colorScheme.onPrimary
        ReviewStepState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = colors) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(6.dp))
            Column {
                Text("Step $stepIndex", style = MaterialTheme.typography.labelMedium, color = onC)
                Text(title, style = MaterialTheme.typography.bodySmall, color = onC, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PhaseSelectPrescription(
    modifier: Modifier = Modifier,
    rx: ReviewPresetPrescriptionDemo,
    sourceDescription: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("第 1 步：选择处方", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "以下为当前案例处方（可替换为搜索/扫码）。来源：$sourceDescription",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PrescriptionSummaryCard(rx)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("已选定该处方，进入拍照识图")
        }
    }
}

@Composable
private fun PrescriptionSummaryCard(rx: ReviewPresetPrescriptionDemo) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("处方编号：${rx.prescriptionNo}", style = MaterialTheme.typography.titleSmall)
            rx.diagnosis?.takeIf { it.isNotBlank() }?.let {
                Text("诊断：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rx.patientGender?.let { Text("性别：$it", style = MaterialTheme.typography.bodySmall) }
                rx.patientAge?.let { Text("年龄：$it", style = MaterialTheme.typography.bodySmall) }
            }
            rx.prescribedAt?.takeIf { it.isNotBlank() }?.let {
                Text("开方日期：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text("药材清单（${rx.lines.size} 味）", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                rx.lines.forEach { line ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${line.lineNo}. ${line.herbName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            listOfNotNull(line.dosage, line.usageMethod).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseStreamDemoPanel(apiConfigured: Boolean) {
    var stream by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        stream = ""
        val sample =
            "【模拟流式】ChatGLM3 可对拍摄图像逐段输出药材名与简要依据；生产环境可对接 SSE 或 OpenAI 兼容流式端点。此处为本地演示。"
        for (i in sample.indices step 3) {
            delay(32)
            stream = sample.substring(0, (i + 3).coerceAtMost(sample.length))
        }
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("流式识别（演示）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (apiConfigured) {
                    "已配置 API：后续可将本区域绑定到后端 ChatGLM3 流式识别通道。"
                } else {
                    "未配置 herbApi.baseUrl：仅本地动画，不发起网络请求。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(stream, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepErrorReportAlertDialog(
    step: ReviewFlowStepDemo,
    sessionId: String,
    onDismiss: () -> Unit,
    onSuccess: (sessionStepId: Int, successText: String, action: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sid = step.sessionStepId ?: return
    var reportDescription by remember { mutableStateOf("") }
    var adjustedHerb by remember { mutableStateOf("") }
    var reportSubmitting by remember { mutableStateOf(false) }
    var reportDialogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sid) {
        reportDescription = ""
        reportDialogError = null
    }

    AlertDialog(
        onDismissRequest = { if (!reportSubmitting) onDismiss() },
        title = { Text("上报识别错误 · 步骤 ${step.stepIndex}") },
        text = {
            Column {
                Text(
                    "${step.herbName} · 识别：${step.llmRecognizedName ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = adjustedHerb,
                    onValueChange = { adjustedHerb = it; reportDialogError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("直接修正为（可选）") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reportDescription,
                    onValueChange = { reportDescription = it; reportDialogError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("报错说明（可选）") },
                    minLines = 2,
                )
                if (reportDialogError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(reportDialogError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = !reportSubmitting && adjustedHerb.isNotBlank(),
                    onClick = {
                        scope.launch {
                            reportSubmitting = true
                            reportDialogError = null
                            try {
                                HerbReviewRemote.ensureLoggedIn()
                                val adjusted = adjustedHerb.trim()
                                HerbReviewRemote.patchReviewStep(
                                    sessionId = sessionId,
                                    stepId = sid,
                                    recognizedName = adjusted,
                                    matchStatus = if (adjusted == step.herbName) "correct" else "needs_review",
                                    reviewerComment = "manual_fix from=${step.llmRecognizedName ?: "—"} to=$adjusted",
                                )
                                onSuccess(
                                    sid,
                                    "已直接修正并记录：${step.llmRecognizedName ?: "—"} → $adjusted",
                                    "adjusted",
                                )
                            } catch (e: Exception) {
                                reportDialogError = e.message ?: e.toString()
                            } finally {
                                reportSubmitting = false
                            }
                        }
                    },
                ) {
                    Text("直接修正")
                }
                TextButton(
                    enabled = !reportSubmitting,
                    onClick = {
                        scope.launch {
                            reportSubmitting = true
                            reportDialogError = null
                            try {
                                HerbReviewRemote.ensureLoggedIn()
                                val res = HerbReviewRemote.reportSessionStepError(
                                    sessionId = sessionId,
                                    stepId = sid,
                                    description = reportDescription.takeIf { it.isNotBlank() },
                                )
                                onSuccess(
                                    sid,
                                    "已提交工单 ER-${res.errorReportId}（${res.status}），该会话已挂起，等待报错台裁决",
                                    "reported",
                                )
                            } catch (e: HttpException) {
                                val err = e.response()?.errorBody()?.string()?.take(200)
                                reportDialogError = err ?: (e.message ?: e.toString())
                            } catch (e: Exception) {
                                reportDialogError = e.message ?: e.toString()
                            } finally {
                                reportSubmitting = false
                            }
                        }
                    },
                ) {
                    if (reportSubmitting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("仅报错")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !reportSubmitting) { Text("取消") }
        },
    )
}

@Composable
private fun PhaseCapture(
    herbNames: List<String>,
    capturedCount: Int,
    apiConfigured: Boolean,
    sessionId: String?,
    orderedRemoteFlow: List<ReviewFlowStepDemo>?,
    canSubmitErrorReport: Boolean,
    onCaptureOne: () -> Unit,
    onUndoOne: () -> Unit,
    onSkipToResult: () -> Unit,
    onNext: () -> Unit,
) {
    val lines = herbNames
    val nextLine = lines.getOrNull(capturedCount)
    val ordered = orderedRemoteFlow?.sortedBy { it.stepIndex }.orEmpty()
    val currentStep = ordered.getOrNull(capturedCount)
    val progressRatio = if (lines.isEmpty()) 0f else capturedCount.toFloat() / lines.size.toFloat()
    var reportTarget by remember { mutableStateOf<ReviewFlowStepDemo?>(null) }
    var reportedStepIds by remember { mutableStateOf(setOf<Int>()) }
    var lastReportSuccess by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Text("第 2 步：顺序拍照识图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请按处方清单从上到下逐味拍摄。主操作在上方，向下滚动可查看清单与流式演示。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "进度：$capturedCount / ${lines.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progressRatio }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        val sid = currentStep?.sessionStepId
        val canReportStep =
            apiConfigured &&
                !sessionId.isNullOrBlank() &&
                sid != null &&
                canSubmitErrorReport &&
                sid !in reportedStepIds &&
                currentStep?.hasErrorReport == false
        if (lastReportSuccess != null) {
            Text(
                lastReportSuccess!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (canReportStep) {
            TextButton(onClick = { reportTarget = currentStep }) {
                Text("上报当前步识别问题")
            }
            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = onCaptureOne,
            enabled = nextLine != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (nextLine != null) {
                    "模拟拍摄：$nextLine"
                } else {
                    "已完成全部拍摄"
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onUndoOne,
            enabled = capturedCount > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("回退上一步拍摄")
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onSkipToResult,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("跳过拍摄，直接查看识别结果表（演示）")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onNext,
            enabled = capturedCount >= lines.size,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("进入结果表")
        }

        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 160.dp)) {
            PhaseStreamDemoPanel(apiConfigured = apiConfigured)
        }
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            lines.forEachIndexed { idx, herbName ->
                val status = when {
                    idx < capturedCount -> "已拍摄（模拟）"
                    idx == capturedCount -> "当前待拍"
                    else -> "待拍摄"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${idx + 1}. $herbName", style = MaterialTheme.typography.bodyMedium)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (idx < lines.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }

    reportTarget?.let { step ->
        if (step.sessionStepId == null) return@let
        val sidSession = sessionId ?: return@let
        StepErrorReportAlertDialog(
            step = step,
            sessionId = sidSession,
            onDismiss = { reportTarget = null },
            onSuccess = { reportedId, msg, action ->
                reportedStepIds = reportedStepIds + reportedId
                lastReportSuccess = if (action == "adjusted") {
                    "$msg（修改操作已记录）"
                } else {
                    msg
                }
                reportTarget = null
            },
        )
    }
}

@Composable
private fun PhaseResultTable(
    flow: List<ReviewFlowStepDemo>,
    sessionId: String?,
    apiConfigured: Boolean,
    canSubmitErrorReport: Boolean,
    finalizeOutcome: IntegrationOutcome,
    onReturnForRecheck: () -> Unit,
    onFinishAndArchive: () -> Unit,
    onStartNewReview: () -> Unit,
    onExitWorkbench: () -> Unit,
) {
    val ordered = flow.sortedBy { it.stepIndex }
    val summary = ReviewResultSummary.from(ordered)
    var rowFilter by rememberSaveable { mutableStateOf("all") }
    var reportTarget by remember { mutableStateOf<ReviewFlowStepDemo?>(null) }
    var reportedStepIds by remember { mutableStateOf(setOf<Int>()) }
    var lastReportSuccess by remember { mutableStateOf<String?>(null) }
    val hasRemainingIssues = summary.incorrect + summary.needsReview + summary.pending > 0
    val filtered = ordered.filter { step ->
        when (rowFilter) {
            "issue" -> step.matchStatus == "incorrect" || step.matchStatus == "needs_review"
            "pending" -> step.matchStatus == "pending"
            else -> true
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text("第 3 步：识别结果与人工复核", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "远程会话下可对单步「上报识别错误」，写入 error_reports 并在报错台可见；离线演示数据无步骤 id 时不显示按钮。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastReportSuccess != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    lastReportSuccess!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            ResultSummaryCard(
                summary = summary,
                sessionId = sessionId,
                finalizeOutcome = finalizeOutcome,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = rowFilter == "all",
                    onClick = { rowFilter = "all" },
                    label = { Text("全部 ${ordered.size}") },
                )
                FilterChip(
                    selected = rowFilter == "issue",
                    onClick = { rowFilter = "issue" },
                    label = { Text("问题 ${summary.incorrect + summary.needsReview}") },
                )
                FilterChip(
                    selected = rowFilter == "pending",
                    onClick = { rowFilter = "pending" },
                    label = { Text("待识别 ${summary.pending}") },
                )
            }
            Spacer(Modifier.height(8.dp))

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
                    Text("人工复核 / 操作", modifier = Modifier.weight(0.37f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            filtered.forEachIndexed { idx, step ->
                if (idx > 0) {
                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
                val sid = step.sessionStepId
                val canReport = apiConfigured &&
                    sessionId != null &&
                    sid != null &&
                    canSubmitErrorReport &&
                    sid !in reportedStepIds &&
                    !step.hasErrorReport
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(0.26f)) {
                        Text(
                            text = "${step.stepIndex}. ${step.herbName}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        when {
                            step.hasErrorReport -> Text(
                                "已有报错记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            sid != null && sid in reportedStepIds -> Text(
                                "本步已上报",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            canReport -> TextButton(onClick = { reportTarget = step }) {
                                Text("上报识别错误", style = MaterialTheme.typography.labelSmall)
                            }
                            apiConfigured && sessionId != null && !canSubmitErrorReport -> Text(
                                "无报错权限",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = recognitionCell(step),
                        modifier = Modifier.weight(0.37f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = manualReviewCell(step),
                        modifier = Modifier.weight(0.37f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        if (apiConfigured && sessionId != null && hasRemainingIssues) {
            FilledTonalButton(
                onClick = onReturnForRecheck,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("打回并仅复核问题药材（${summary.incorrect + summary.needsReview + summary.pending} 项）")
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = "确认识别结果后，请使用「完成并归档」：将会话标为已完成并写入服务端（科主任「工作分析」可见汇总），同时在「我的 · 复核历史」留下记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ResultActionButtons(
            onFinishAndArchive = onFinishAndArchive,
            onStartNewReview = onStartNewReview,
            onExitWorkbench = onExitWorkbench,
        )
    }

    reportTarget?.let { step ->
        if (step.sessionStepId == null) return@let
        val sidSession = sessionId ?: return@let
        StepErrorReportAlertDialog(
            step = step,
            sessionId = sidSession,
            onDismiss = { reportTarget = null },
            onSuccess = { reportedId, msg, action ->
                reportedStepIds = reportedStepIds + reportedId
                lastReportSuccess = if (action == "adjusted") {
                    "$msg（修改操作已记录）"
                } else {
                    msg
                }
                reportTarget = null
            },
        )
    }
}

private data class ReviewResultSummary(
    val total: Int,
    val correct: Int,
    val incorrect: Int,
    val needsReview: Int,
    val pending: Int,
) {
    companion object {
        fun from(steps: List<ReviewFlowStepDemo>): ReviewResultSummary {
            val total = steps.size
            val correct = steps.count { it.matchStatus == "correct" }
            val incorrect = steps.count { it.matchStatus == "incorrect" }
            val needsReview = steps.count { it.matchStatus == "needs_review" }
            val pending = steps.count { it.matchStatus == "pending" }
            return ReviewResultSummary(total, correct, incorrect, needsReview, pending)
        }
    }
}

@Composable
private fun ResultSummaryCard(
    summary: ReviewResultSummary,
    sessionId: String?,
    finalizeOutcome: IntegrationOutcome,
) {
    val clipboard = LocalClipboardManager.current
    var copiedHint by remember { mutableStateOf(false) }
    val summaryText = buildSessionSummaryText(summary, sessionId, finalizeOutcome)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("会话总结", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            sessionId?.let {
                Text(
                    text = "会话号：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatMini("总步骤", summary.total.toString(), Modifier.weight(1f))
                StatMini("一致", summary.correct.toString(), Modifier.weight(1f))
                StatMini("不符", summary.incorrect.toString(), Modifier.weight(1f))
                StatMini("存疑", summary.needsReview.toString(), Modifier.weight(1f))
                StatMini("待识别", summary.pending.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = when (finalizeOutcome) {
                    is IntegrationOutcome.Ok -> "归档：${finalizeOutcome.detail}"
                    is IntegrationOutcome.Fail -> "归档失败：${finalizeOutcome.message}"
                    IntegrationOutcome.Working -> "归档中…"
                    IntegrationOutcome.Waiting -> "归档未执行"
                    IntegrationOutcome.NotApplicable -> "离线模式未归档"
                    IntegrationOutcome.Hidden -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (finalizeOutcome) {
                    is IntegrationOutcome.Fail -> MaterialTheme.colorScheme.error
                    is IntegrationOutcome.Ok -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(summaryText))
                        copiedHint = true
                    },
                ) {
                    Text("复制摘要")
                }
                if (copiedHint) {
                    Text(
                        text = "已复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun buildSessionSummaryText(
    summary: ReviewResultSummary,
    sessionId: String?,
    finalizeOutcome: IntegrationOutcome,
): String {
    val archive = when (finalizeOutcome) {
        is IntegrationOutcome.Ok -> "归档成功：${finalizeOutcome.detail}"
        is IntegrationOutcome.Fail -> "归档失败：${finalizeOutcome.message}"
        IntegrationOutcome.Working -> "归档中"
        IntegrationOutcome.Waiting -> "归档未执行"
        IntegrationOutcome.NotApplicable -> "离线模式未归档"
        IntegrationOutcome.Hidden -> ""
    }
    return buildString {
        append("复核会话总结\n")
        if (!sessionId.isNullOrBlank()) append("会话号：$sessionId\n")
        append("总步骤：${summary.total}，一致：${summary.correct}，不符：${summary.incorrect}，存疑：${summary.needsReview}，待识别：${summary.pending}\n")
        append(archive)
    }
}

@Composable
private fun StatMini(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultActionButtons(
    onFinishAndArchive: () -> Unit,
    onStartNewReview: () -> Unit,
    onExitWorkbench: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onFinishAndArchive,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("完成并归档（返回工作台）")
        }
        Text(
            text = "若从第 2 步进入结果表时已自动归档，此处会保持 completed；仍会写入您的本地复核摘要。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { confirmNew = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("开始新的复核（本页重置）")
        }
        TextButton(
            onClick = { confirmExit = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("离开复核页")
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("离开复核？") },
            text = {
                Text(
                    "仅返回工作台，不会自动归档当前会话；未点「完成并归档」时会话可能在服务器上仍为进行中，可在工作台历史继续。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        onExitWorkbench()
                    },
                ) { Text("仍要离开") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("取消") }
            },
        )
    }
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("开始新的复核？") },
            text = {
                Text(
                    "将清空本页进度并回到第 1 步。若当前会话尚未归档，可在工作台「我的复核会话」中找回。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmNew = false
                        onStartNewReview()
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) { Text("取消") }
            },
        )
    }
}

private fun recognitionCell(step: ReviewFlowStepDemo): String {
    val base = step.llmRecognizedName ?: "—"
    return "$base（${matchStatusLabel(step.matchStatus)}）"
}

private fun manualReviewCell(step: ReviewFlowStepDemo): String {
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

private fun decisionLabel(decision: String?): String = when (decision) {
    "confirm_error" -> "确认有误"
    "reject_error" -> "驳回"
    "adjust_recognition" -> "采纳药名"
    else -> decision ?: "—"
}

@Preview(showBackground = true, showSystemUi = true, name = "Review Home")
@Composable
private fun ReviewHomeScreenPreview() {
    HerbReviewTheme {
        ReviewHomeScreen(
            session = PharmacistSession(
                employeeId = "3070",
                displayName = "药师（3070）",
                isDepartmentDirector = false,
                canSubmitErrorReport = true,
            ),
        )
    }
}
