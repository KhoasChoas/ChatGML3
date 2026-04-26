from __future__ import annotations

from app.schemas.common import ConfigModel


class DirectorWorkRow(ConfigModel):
    session_id: str
    session_started_at: str | None = None
    session_status: str | None = None
    prescription_no: str | None = None
    patient_name: str | None = None
    diagnosis: str | None = None
    created_by_name: str | None = None
    created_by_employee_id: str | None = None
    step_total: int | None = None
    step_correct: int | None = None
    step_incorrect: int | None = None
    step_needs_review: int | None = None
    step_manual_fix: int | None = None
    error_reports_filed: int | None = None
    error_reports_pending: int | None = None
    error_reports_resolved: int | None = None
    return_count: int | None = None
    reviewing_doctor: str | None = None
    reviewing_doctor_employee_id: str | None = None


class DirectorAuditLogRow(ConfigModel):
    id: int
    action: str
    session_id: str
    detail: str | None = None
    created_at: str
    pharmacist_name: str | None = None
    pharmacist_employee_id: str | None = None


class DirectorErrorTimelineRow(ConfigModel):
    error_report_id: int
    session_id: str | None = None
    description: str | None = None
    reported_at: str | None = None
    report_status: str | None = None
    step_index: int | None = None
    expected_herb_name: str | None = None
    llm_recognized_name: str | None = None
    reported_by_name: str | None = None
    prescription_no: str | None = None
    review_id: int | None = None
    reviewed_at: str | None = None
    reviewer_name: str | None = None
    decision: str | None = None
    agreed_herb_name: str | None = None
    review_comment: str | None = None
