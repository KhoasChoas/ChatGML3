package com.zhipu.herbreview.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HerbReviewApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenDto

    @GET("prescriptions")
    suspend fun listPrescriptions(
        @Query("q") q: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): PrescriptionPageDto

    @GET("prescriptions/{id}")
    suspend fun getPrescription(@Path("id") id: Int): PrescriptionDetailDto

    @POST("review-sessions")
    suspend fun createReviewSession(@Body body: CreateReviewSessionDto): ReviewSessionDto

    @GET("review-sessions/{id}")
    suspend fun getReviewSession(@Path("id") id: String): ReviewSessionDto

    @GET("review-sessions")
    suspend fun listReviewSessions(
        @Query("limit") limit: Int,
        @Query("prescription_id") prescriptionId: Int? = null,
        @Query("mine") mine: Boolean? = null,
    ): List<ReviewSessionDto>

    @PATCH("review-sessions/{sessionId}/steps/{stepId}")
    suspend fun patchSessionStep(
        @Path("sessionId") sessionId: String,
        @Path("stepId") stepId: Int,
        @Body body: SessionStepPatchDto,
    ): SessionStepDto

    @POST("review-sessions/{sessionId}/steps/{stepId}/error-report")
    suspend fun reportSessionStepError(
        @Path("sessionId") sessionId: String,
        @Path("stepId") stepId: Int,
        @Body body: SessionStepErrorReportBodyDto,
    ): ErrorReportCreatedDto

    @PATCH("review-sessions/{sessionId}/status")
    suspend fun patchSessionStatus(
        @Path("sessionId") sessionId: String,
        @Body body: SessionStatusPatchDto,
    ): ReviewSessionDto

    @POST("review-sessions/{sessionId}/return")
    suspend fun returnSessionForRecheck(
        @Path("sessionId") sessionId: String,
    ): ReviewSessionDto

    @GET("analytics/director/work-overview")
    suspend fun directorWorkOverview(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): List<DirectorWorkRowDto>

    @GET("analytics/director/error-timeline")
    suspend fun directorErrorTimeline(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): List<DirectorErrorTimelineDto>

    @GET("analytics/director/audit-logs")
    suspend fun directorAuditLogs(
        @Query("session_id") sessionId: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): List<DirectorAuditLogDto>

    @GET("error-reports")
    suspend fun listErrorReports(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("status") status: String? = null,
        @Query("pending_only") pendingOnly: Boolean? = null,
        @Query("reviewed_only") reviewedOnly: Boolean? = null,
    ): List<DirectorErrorTimelineDto>

    @POST("error-reports/{reportId}/reviews")
    suspend fun submitErrorReportReview(
        @Path("reportId") reportId: Int,
        @Body body: ErrorReportReviewCreateDto,
    ): DirectorErrorTimelineDto
}
