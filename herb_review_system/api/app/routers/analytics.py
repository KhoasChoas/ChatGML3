from __future__ import annotations

from fastapi import APIRouter, Query
from sqlalchemy import text

from app.deps import DbSession
from app.schemas.analytics import DirectorAuditLogRow, DirectorErrorTimelineRow, DirectorWorkRow

router = APIRouter()


@router.get("/director/work-overview", response_model=list[DirectorWorkRow])
def director_work_overview(
    db: DbSession,
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
) -> list[DirectorWorkRow]:
    db.execute(
        text(
            """
            CREATE TABLE IF NOT EXISTS review_audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                session_id TEXT NOT NULL,
                pharmacist_id INTEGER NOT NULL,
                detail TEXT,
                created_at TEXT NOT NULL
            )
            """
        )
    )
    result = db.execute(
        text(
            """
            SELECT
                rs.id AS session_id,
                rs.created_at AS session_started_at,
                rs.status AS session_status,
                pr.prescription_no,
                pr.patient_name,
                pr.diagnosis,
                creator.full_name AS created_by_name,
                creator.employee_id AS created_by_employee_id,
                (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id) AS step_total,
                (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id AND ss.match_status = 'correct') AS step_correct,
                (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id AND ss.match_status = 'incorrect') AS step_incorrect,
                (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id AND ss.match_status = 'needs_review') AS step_needs_review,
                (
                    SELECT COUNT(*)
                    FROM session_steps ss
                    WHERE ss.session_id = rs.id
                      AND (
                        COALESCE(ss.reviewer_comment, '') LIKE '%manual_fix%'
                        OR COALESCE(ss.reviewer_comment, '') LIKE '%inbox_adjust%'
                        OR COALESCE(ss.reviewer_comment, '') LIKE '%adjust%'
                      )
                ) AS step_manual_fix,
                (SELECT COUNT(*) FROM error_reports er WHERE er.session_id = rs.id) AS error_reports_filed,
                (SELECT COUNT(*) FROM error_reports er WHERE er.session_id = rs.id AND er.status != 'resolved') AS error_reports_pending,
                (SELECT COUNT(*) FROM error_reports er WHERE er.session_id = rs.id AND er.status = 'resolved') AS error_reports_resolved,
                (
                    SELECT COUNT(*)
                    FROM review_audit_logs al
                    WHERE al.session_id = rs.id
                      AND al.action IN ('review_returned', 'error_inbox_auto_return')
                ) AS return_count,
                pr.reviewing_doctor AS reviewing_doctor,
                (
                    SELECT p.employee_id
                    FROM review_audit_logs al
                    JOIN pharmacists p ON p.id = al.pharmacist_id
                    WHERE al.session_id = rs.id
                      AND al.action IN ('review_completed', 'error_inbox_auto_pass')
                    ORDER BY al.created_at DESC, al.id DESC
                    LIMIT 1
                ) AS reviewing_doctor_employee_id
            FROM review_sessions rs
            JOIN prescriptions pr ON pr.id = rs.prescription_id
            JOIN pharmacists creator ON creator.id = rs.created_by_pharmacist_id
            ORDER BY rs.created_at DESC
            LIMIT :lim OFFSET :off
            """
        ),
        {"lim": limit, "off": offset},
    )
    rows = [DirectorWorkRow.model_validate(dict(r._mapping)) for r in result]
    return rows


@router.get("/director/error-timeline", response_model=list[DirectorErrorTimelineRow])
def director_error_timeline(
    db: DbSession,
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
) -> list[DirectorErrorTimelineRow]:
    result = db.execute(
        text(
            "SELECT * FROM director_error_resolution_timeline "
            "ORDER BY reported_at DESC LIMIT :lim OFFSET :off",
        ),
        {"lim": limit, "off": offset},
    )
    rows = [DirectorErrorTimelineRow.model_validate(dict(r._mapping)) for r in result]
    return rows


@router.get("/director/audit-logs", response_model=list[DirectorAuditLogRow])
def director_audit_logs(
    db: DbSession,
    session_id: str | None = Query(default=None, description="仅查看指定复核会话的审计记录；不传则返回全局最近记录"),
    limit: int = Query(default=200, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
) -> list[DirectorAuditLogRow]:
    db.execute(
        text(
            """
            CREATE TABLE IF NOT EXISTS review_audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                session_id TEXT NOT NULL,
                pharmacist_id INTEGER NOT NULL,
                detail TEXT,
                created_at TEXT NOT NULL
            )
            """
        )
    )
    sid = (session_id or "").strip()
    base_sql = """
        SELECT
            al.id AS id,
            al.action AS action,
            al.session_id AS session_id,
            al.detail AS detail,
            al.created_at AS created_at,
            p.full_name AS pharmacist_name,
            p.employee_id AS pharmacist_employee_id
        FROM review_audit_logs al
        JOIN pharmacists p ON p.id = al.pharmacist_id
    """
    if sid:
        result = db.execute(
            text(base_sql + " WHERE al.session_id = :sid ORDER BY al.created_at DESC, al.id DESC LIMIT :lim OFFSET :off"),
            {"sid": sid, "lim": limit, "off": offset},
        )
    else:
        result = db.execute(
            text(base_sql + " ORDER BY al.created_at DESC, al.id DESC LIMIT :lim OFFSET :off"),
            {"lim": limit, "off": offset},
        )
    return [DirectorAuditLogRow.model_validate(dict(r._mapping)) for r in result]
