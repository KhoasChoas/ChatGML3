package com.zhipu.herbreview.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.zhipu.herbreview.BuildConfig
import com.zhipu.herbreview.data.DirectorAuditLogRow
import com.zhipu.herbreview.data.DirectorErrorTimelineRow
import com.zhipu.herbreview.data.DirectorSessionStepRow
import com.zhipu.herbreview.data.DirectorWorkOverviewRow
import com.zhipu.herbreview.data.ReviewFlowStepDemo
import com.zhipu.herbreview.data.ReviewPresetPrescriptionDemo
import com.zhipu.herbreview.data.ReviewPrescriptionLineDemo
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private object HerbApiAuth {
    @Volatile
    var bearerToken: String? = null

    @Volatile
    var devPharmacistId: String? = null
}

object HerbReviewRemote {

    private val gson: Gson = GsonBuilder().serializeNulls().create()

    private val api: HerbReviewApi? by lazy {
        val base = BuildConfig.HERB_API_BASE_URL.trim().trimEnd('/')
        if (base.isEmpty()) return@lazy null
        val normalized = "$base/api/v1/"
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val auth = Interceptor { chain ->
            val token = HerbApiAuth.bearerToken
            val devId = HerbApiAuth.devPharmacistId
            val builder = chain.request().newBuilder()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            } else if (!devId.isNullOrBlank()) {
                builder.header("X-Dev-Pharmacist-Id", devId)
            }
            val req = builder.build()
            chain.proceed(req)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HerbReviewApi::class.java)
    }

    fun isConfigured(): Boolean = BuildConfig.HERB_API_BASE_URL.isNotBlank()

    suspend fun ensureLoggedIn() {
        val service = api ?: return
        val eid = BuildConfig.HERB_API_LOGIN_EMPLOYEE_ID.trim()
        val pw = BuildConfig.HERB_API_LOGIN_PASSWORD
        val devId = BuildConfig.HERB_API_DEV_PHARMACIST_ID.trim()
        HerbApiAuth.bearerToken = null
        HerbApiAuth.devPharmacistId = null
        if (eid.isEmpty()) {
            if (devId.isNotEmpty()) {
                HerbApiAuth.devPharmacistId = devId
                return
            }
            throw IllegalStateException("local.properties：请设置 herbApi.loginEmployeeId 或 herbApi.devPharmacistId")
        }
        val body = LoginRequestDto(employeeId = eid, password = pw)
        try {
            val token = service.login(body).accessToken
            HerbApiAuth.bearerToken = token
        } catch (e: Exception) {
            if (devId.isNotEmpty()) {
                HerbApiAuth.devPharmacistId = devId
                HerbApiAuth.bearerToken = null
                return
            }
            if (e is HttpException && e.code() == 401) {
                throw IllegalStateException(
                    "登录失败 401：请检查 herbApi.loginPassword，或配置 herbApi.devPharmacistId 走开发头认证",
                )
            }
            throw e
        }
    }

    suspend fun fetchPresetPrescription(): PrescriptionDetailDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        val q = BuildConfig.HERB_API_PRESET_PRESCRIPTION_NO.trim()
        val page = service.listPrescriptions(q = q, limit = 30, offset = 0)
        val first = page.items.firstOrNull { it.prescriptionNo == q }
            ?: page.items.firstOrNull()
            ?: throw IllegalStateException("未找到处方：$q")
        return service.getPrescription(first.id)
    }

    suspend fun fetchPrescriptionById(id: Int): PrescriptionDetailDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.getPrescription(id)
    }

    suspend fun createReviewSession(prescriptionId: Int): ReviewSessionDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.createReviewSession(CreateReviewSessionDto(prescriptionId = prescriptionId))
    }

    suspend fun fetchReviewSession(sessionId: String): ReviewSessionDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.getReviewSession(sessionId)
    }

    suspend fun fetchRecentReviewSessions(
        limit: Int = 20,
        prescriptionId: Int? = null,
        mine: Boolean? = null,
    ): List<ReviewSessionDto> {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.listReviewSessions(limit = limit, prescriptionId = prescriptionId, mine = mine)
    }

    suspend fun reportSessionStepError(
        sessionId: String,
        stepId: Int,
        description: String? = null,
    ): ErrorReportCreatedDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.reportSessionStepError(
            sessionId = sessionId,
            stepId = stepId,
            body = SessionStepErrorReportBodyDto(description = description),
        )
    }

    suspend fun patchReviewStep(
        sessionId: String,
        stepId: Int,
        recognizedName: String?,
        matchStatus: String,
        reviewerComment: String = "模拟拍摄回写",
    ): SessionStepDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.patchSessionStep(
            sessionId = sessionId,
            stepId = stepId,
            body = SessionStepPatchDto(
                llmRecognizedName = recognizedName,
                matchStatus = matchStatus,
                reviewerComment = reviewerComment,
            ),
        )
    }

    suspend fun completeReviewSession(sessionId: String): ReviewSessionDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.patchSessionStatus(
            sessionId = sessionId,
            body = SessionStatusPatchDto(status = "completed"),
        )
    }

    suspend fun returnSessionForRecheck(sessionId: String): ReviewSessionDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.returnSessionForRecheck(sessionId = sessionId)
    }

    suspend fun fetchDirectorOverview(): List<DirectorWorkRowDto> {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.directorWorkOverview(limit = 200, offset = 0)
    }

    suspend fun fetchDirectorErrorTimeline(limit: Int = 500, offset: Int = 0): List<DirectorErrorTimelineDto> {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.directorErrorTimeline(limit = limit.coerceIn(1, 500), offset = offset.coerceAtLeast(0))
    }

    suspend fun fetchDirectorAuditLogs(sessionId: String? = null, limit: Int = 200, offset: Int = 0): List<DirectorAuditLogDto> {
        val service = api ?: throw IllegalStateException("API 未配置")
        val sid = sessionId?.trim()?.ifEmpty { null }
        return service.directorAuditLogs(
            sessionId = sid,
            limit = limit.coerceIn(1, 500),
            offset = offset.coerceAtLeast(0),
        )
    }

    suspend fun searchPrescriptions(q: String, limit: Int = 40): PrescriptionPageDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        val trimmed = q.trim()
        return service.listPrescriptions(
            q = trimmed.ifEmpty { null },
            limit = limit.coerceIn(1, 200),
            offset = 0,
        )
    }

    suspend fun fetchErrorReportsInbox(
        limit: Int = 80,
        offset: Int = 0,
        status: String? = null,
        pendingOnly: Boolean? = null,
        reviewedOnly: Boolean? = null,
    ): List<DirectorErrorTimelineDto> {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.listErrorReports(
            limit = limit.coerceIn(1, 500),
            offset = offset,
            status = status,
            pendingOnly = pendingOnly,
            reviewedOnly = reviewedOnly,
        )
    }

    suspend fun submitErrorReportReview(
        reportId: Int,
        decision: String,
        agreedHerbName: String? = null,
        comment: String? = null,
    ): DirectorErrorTimelineDto {
        val service = api ?: throw IllegalStateException("API 未配置")
        return service.submitErrorReportReview(
            reportId = reportId,
            body = ErrorReportReviewCreateDto(
                decision = decision,
                agreedHerbName = agreedHerbName,
                comment = comment,
            ),
        )
    }
}

fun PrescriptionDetailDto.toPresetDemo(): ReviewPresetPrescriptionDemo =
    ReviewPresetPrescriptionDemo(
        prescriptionNo = prescriptionNo,
        diagnosis = diagnosis,
        patientGender = patientGender,
        patientAge = patientAge,
        prescribedAt = prescribedAt,
        lines = items.map {
            ReviewPrescriptionLineDemo(
                lineNo = it.lineNo,
                herbName = it.herbName,
                dosage = it.dosage,
                usageMethod = it.usageMethod,
            )
        },
    )

fun ReviewSessionDto.toFlowSteps(): List<ReviewFlowStepDemo> =
    steps.sortedBy { it.stepIndex }.map { s ->
        ReviewFlowStepDemo(
            stepIndex = s.stepIndex,
            herbName = s.expectedHerbName,
            llmRecognizedName = s.llmRecognizedName,
            matchStatus = s.matchStatus,
            reviewDecision = null,
            reviewAgreedHerbName = null,
            reviewerName = null,
            reviewComment = s.reviewerComment,
            hasErrorReport = false,
            sessionStepId = s.id,
        )
    }

fun DirectorWorkRowDto.toOverviewRow(): DirectorWorkOverviewRow =
    DirectorWorkOverviewRow(
        sessionId = sessionId,
        sessionStartedAt = sessionStartedAt.orEmpty(),
        sessionStatus = sessionStatus.orEmpty(),
        prescriptionNo = prescriptionNo.orEmpty(),
        patientName = patientName,
        diagnosis = diagnosis,
        createdByName = createdByName.orEmpty(),
        createdByEmployeeId = createdByEmployeeId.orEmpty(),
        stepTotal = stepTotal ?: 0,
        stepCorrect = stepCorrect ?: 0,
        stepIncorrect = stepIncorrect ?: 0,
        stepNeedsReview = stepNeedsReview ?: 0,
        stepManualFix = stepManualFix ?: 0,
        errorReportsFiled = errorReportsFiled ?: 0,
        errorReportsPending = errorReportsPending ?: 0,
        errorReportsResolved = errorReportsResolved ?: 0,
        returnCount = returnCount ?: 0,
        reviewingDoctor = reviewingDoctor,
        reviewingDoctorEmployeeId = reviewingDoctorEmployeeId,
    )

fun SessionStepDto.toDirectorStep(sessionId: String): DirectorSessionStepRow =
    DirectorSessionStepRow(
        sessionId = sessionId,
        stepIndex = stepIndex,
        herbName = expectedHerbName,
        llmRecognizedName = llmRecognizedName,
        matchStatus = matchStatus,
        reviewDecision = null,
        reviewAgreedHerbName = null,
        reviewerName = null,
        reviewComment = reviewerComment,
        hasErrorReport = false,
    )

fun DirectorAuditLogDto.toAuditLogRow(): DirectorAuditLogRow =
    DirectorAuditLogRow(
        id = id,
        action = action,
        sessionId = sessionId,
        detail = detail,
        createdAt = createdAt,
        pharmacistName = pharmacistName,
        pharmacistEmployeeId = pharmacistEmployeeId,
    )

fun DirectorErrorTimelineDto.toErrorTimelineRow(): DirectorErrorTimelineRow =
    DirectorErrorTimelineRow(
        errorReportId = errorReportId,
        reportedAt = reportedAt.orEmpty(),
        reportStatus = reportStatus.orEmpty(),
        stepIndex = stepIndex ?: 0,
        expectedHerbName = expectedHerbName.orEmpty(),
        llmRecognizedName = llmRecognizedName,
        reportedByName = reportedByName.orEmpty(),
        prescriptionNo = prescriptionNo.orEmpty(),
        reviewId = reviewId,
        reviewedAt = reviewedAt,
        reviewerName = reviewerName,
        decision = decision,
        agreedHerbName = agreedHerbName,
        reviewComment = reviewComment,
        sessionId = sessionId,
        description = description,
    )
