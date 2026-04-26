package com.zhipu.herbreview.network

import com.google.gson.annotations.SerializedName

data class LoginRequestDto(
    @SerializedName("employee_id") val employeeId: String,
    @SerializedName("password") val password: String,
)

data class TokenDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String?,
)

data class PageMetaDto(
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
)

data class PrescriptionListItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("prescription_no") val prescriptionNo: String,
    @SerializedName("patient_name") val patientName: String?,
    @SerializedName("diagnosis") val diagnosis: String?,
    @SerializedName("prescribed_at") val prescribedAt: String?,
    @SerializedName("herb_kind_count") val herbKindCount: Int?,
)

data class PrescriptionPageDto(
    @SerializedName("items") val items: List<PrescriptionListItemDto>,
    @SerializedName("meta") val meta: PageMetaDto,
)

data class PrescriptionItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("line_no") val lineNo: Int,
    @SerializedName("herb_name") val herbName: String,
    @SerializedName("dosage") val dosage: String?,
    @SerializedName("usage_method") val usageMethod: String?,
)

data class PrescriptionDetailDto(
    @SerializedName("id") val id: Int,
    @SerializedName("prescription_no") val prescriptionNo: String,
    @SerializedName("patient_name") val patientName: String?,
    @SerializedName("diagnosis") val diagnosis: String?,
    @SerializedName("prescribed_at") val prescribedAt: String?,
    @SerializedName("herb_kind_count") val herbKindCount: Int?,
    @SerializedName("patient_gender") val patientGender: String? = null,
    @SerializedName("patient_age") val patientAge: String? = null,
    @SerializedName("items") val items: List<PrescriptionItemDto> = emptyList(),
)

data class CreateReviewSessionDto(
    @SerializedName("prescription_id") val prescriptionId: Int,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("status") val status: String = "in_progress",
    @SerializedName("llm_model_name") val llmModelName: String? = "ChatGLM3",
)

data class SessionStepDto(
    @SerializedName("id") val id: Int,
    @SerializedName("step_index") val stepIndex: Int,
    @SerializedName("prescription_item_id") val prescriptionItemId: Int?,
    @SerializedName("expected_herb_name") val expectedHerbName: String,
    @SerializedName("image_uri") val imageUri: String?,
    @SerializedName("llm_recognized_name") val llmRecognizedName: String?,
    @SerializedName("llm_confidence") val llmConfidence: Double?,
    @SerializedName("match_status") val matchStatus: String,
    @SerializedName("reviewer_comment") val reviewerComment: String?,
)

data class SessionStepPatchDto(
    @SerializedName("llm_recognized_name") val llmRecognizedName: String? = null,
    @SerializedName("match_status") val matchStatus: String? = null,
    @SerializedName("reviewer_comment") val reviewerComment: String? = null,
    @SerializedName("image_uri") val imageUri: String? = null,
)

data class SessionStatusPatchDto(
    @SerializedName("status") val status: String,
)

data class ReviewSessionDto(
    @SerializedName("id") val id: String,
    @SerializedName("prescription_id") val prescriptionId: Int,
    @SerializedName("prescription_no") val prescriptionNo: String? = null,
    @SerializedName("created_by_pharmacist_id") val createdByPharmacistId: Int,
    @SerializedName("status") val status: String,
    @SerializedName("llm_model_name") val llmModelName: String?,
    @SerializedName("notes") val notes: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("steps") val steps: List<SessionStepDto> = emptyList(),
)

data class DirectorWorkRowDto(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("session_started_at") val sessionStartedAt: String?,
    @SerializedName("session_status") val sessionStatus: String?,
    @SerializedName("prescription_no") val prescriptionNo: String?,
    @SerializedName("patient_name") val patientName: String?,
    @SerializedName("diagnosis") val diagnosis: String?,
    @SerializedName("created_by_name") val createdByName: String?,
    @SerializedName("created_by_employee_id") val createdByEmployeeId: String?,
    @SerializedName("step_total") val stepTotal: Int?,
    @SerializedName("step_correct") val stepCorrect: Int?,
    @SerializedName("step_incorrect") val stepIncorrect: Int?,
    @SerializedName("step_needs_review") val stepNeedsReview: Int?,
    @SerializedName("step_manual_fix") val stepManualFix: Int?,
    @SerializedName("error_reports_filed") val errorReportsFiled: Int?,
    @SerializedName("error_reports_pending") val errorReportsPending: Int?,
    @SerializedName("error_reports_resolved") val errorReportsResolved: Int?,
    @SerializedName("return_count") val returnCount: Int?,
    @SerializedName("reviewing_doctor") val reviewingDoctor: String?,
)

data class DirectorErrorTimelineDto(
    @SerializedName("error_report_id") val errorReportId: Int,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("reported_at") val reportedAt: String?,
    @SerializedName("report_status") val reportStatus: String?,
    @SerializedName("step_index") val stepIndex: Int?,
    @SerializedName("expected_herb_name") val expectedHerbName: String?,
    @SerializedName("llm_recognized_name") val llmRecognizedName: String?,
    @SerializedName("reported_by_name") val reportedByName: String?,
    @SerializedName("prescription_no") val prescriptionNo: String?,
    @SerializedName("review_id") val reviewId: Int?,
    @SerializedName("reviewed_at") val reviewedAt: String?,
    @SerializedName("reviewer_name") val reviewerName: String?,
    @SerializedName("decision") val decision: String?,
    @SerializedName("agreed_herb_name") val agreedHerbName: String?,
    @SerializedName("review_comment") val reviewComment: String?,
)

data class ErrorReportReviewCreateDto(
    @SerializedName("decision") val decision: String,
    @SerializedName("agreed_herb_name") val agreedHerbName: String? = null,
    @SerializedName("comment") val comment: String? = null,
)

data class SessionStepErrorReportBodyDto(
    @SerializedName("description") val description: String? = null,
)

data class ErrorReportCreatedDto(
    @SerializedName("error_report_id") val errorReportId: Int,
    @SerializedName("status") val status: String,
)
