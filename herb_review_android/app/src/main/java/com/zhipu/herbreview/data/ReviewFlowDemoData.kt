package com.zhipu.herbreview.data

/**
 * 由 `herb_review_system/database/seed_director_demo.py` 从 prescriptions / prescription_items
 * 及同处方种子复核会话导出，用于「复核」Tab 预设案例。重新跑种子脚本后会更新。
 */
data class ReviewPrescriptionLineDemo(
    val lineNo: Int,
    val herbName: String,
    val dosage: String?,
    val usageMethod: String?,
)

data class ReviewPresetPrescriptionDemo(
    val prescriptionNo: String,
    val diagnosis: String?,
    val patientGender: String?,
    val patientAge: String?,
    val prescribedAt: String?,
    val lines: List<ReviewPrescriptionLineDemo>,
)

data class ReviewFlowStepDemo(
    val stepIndex: Int,
    val herbName: String,
    val llmRecognizedName: String?,
    val matchStatus: String,
    val reviewDecision: String?,
    val reviewAgreedHerbName: String?,
    val reviewerName: String?,
    val reviewComment: String?,
    val hasErrorReport: Boolean,
    /** Populated when flow comes from API session_steps; used for POST error-report. */
    val sessionStepId: Int? = null,
)

object ReviewFlowDemoData {
    /** 与数据库种子脚本 SEED_TAG 对齐，便于排查数据来源 */
    const val DEMO_SOURCE_TAG: String = "seed:director_demo_v3"

    val presetPrescription: ReviewPresetPrescriptionDemo = ReviewPresetPrescriptionDemo(
        prescriptionNo = "365624",
        diagnosis = "肝 功 能 不 全",
        patientGender = "男",
        patientAge = "66 岁",
        prescribedAt = "2026-04-0715:19:04",
        lines = listOf(
ReviewPrescriptionLineDemo(
                lineNo = 1,
                herbName = "苦杏仁",
                dosage = "10g",
                usageMethod = "煎服",
            ),
ReviewPrescriptionLineDemo(
                lineNo = 2,
                herbName = "薏苡仁",
                dosage = "20g",
                usageMethod = "煎服",
            ),
ReviewPrescriptionLineDemo(
                lineNo = 3,
                herbName = "通草",
                dosage = "6g",
                usageMethod = "煎服",
            ),
ReviewPrescriptionLineDemo(
                lineNo = 4,
                herbName = "法半夏",
                dosage = "10g",
                usageMethod = "煎服",
            )
        ),
    )

    /** 与同处方种子会话 session_steps 一致的模拟识别流水（若未找到会话则为全「一致」占位） */
    val simulatedRecognitionFlow: List<ReviewFlowStepDemo> = listOf(
ReviewFlowStepDemo(
                stepIndex = 1,
                herbName = "苦杏仁",
                llmRecognizedName = "苦杏仁",
                matchStatus = "correct",
                reviewDecision = null,
                reviewAgreedHerbName = null,
                reviewerName = null,
                reviewComment = null,
                hasErrorReport = false,
            ),
ReviewFlowStepDemo(
                stepIndex = 2,
                herbName = "薏苡仁",
                llmRecognizedName = "薏苡仁",
                matchStatus = "correct",
                reviewDecision = null,
                reviewAgreedHerbName = null,
                reviewerName = null,
                reviewComment = null,
                hasErrorReport = false,
            ),
ReviewFlowStepDemo(
                stepIndex = 3,
                herbName = "通草",
                llmRecognizedName = "通草（误识）",
                matchStatus = "incorrect",
                reviewDecision = null,
                reviewAgreedHerbName = null,
                reviewerName = null,
                reviewComment = null,
                hasErrorReport = false,
            ),
ReviewFlowStepDemo(
                stepIndex = 4,
                herbName = "法半夏",
                llmRecognizedName = "法半夏",
                matchStatus = "correct",
                reviewDecision = "adjust_recognition",
                reviewAgreedHerbName = "法半夏",
                reviewerName = "刘兆华",
                reviewComment = "模拟：采纳处方应付药名",
                hasErrorReport = true,
            )
    )
}
