from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import text

from app.deps import CurrentPharmacist, CurrentPharmacistId, DbSession
from app.schemas.analytics import DirectorErrorTimelineRow
from app.schemas.error_report import ErrorReportReviewCreate
from app.services.review_sessions import create_session_with_specific_steps
from app.util_time import now_sqlite_text

router = APIRouter()


def _list_sql(where_clause: str) -> str:
    return (
        "SELECT * FROM director_error_resolution_timeline "
        f"WHERE ({where_clause}) "
        "ORDER BY reported_at DESC LIMIT :lim OFFSET :off"
    )


def _append_audit_log(
    db: DbSession,
    *,
    action: str,
    session_id: str,
    pharmacist_id: int,
    detail: str,
) -> None:
    now = now_sqlite_text()
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
        ),
    )
    db.execute(
        text(
            """
            INSERT INTO review_audit_logs (action, session_id, pharmacist_id, detail, created_at)
            VALUES (:action, :sid, :pid, :detail, :now)
            """
        ),
        {
            "action": action,
            "sid": session_id,
            "pid": pharmacist_id,
            "detail": detail,
            "now": now,
        },
    )


@router.get("", response_model=list[DirectorErrorTimelineRow])
def list_error_reports(
    db: DbSession,
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
    status_filter: str | None = Query(
        default=None,
        alias="status",
        description="Filter by error_reports.status (e.g. open, notified, resolved)",
    ),
    pending_only: bool | None = Query(
        default=None,
        description="If true, only rows without a pharmacist review (review_id IS NULL)",
    ),
    reviewed_only: bool | None = Query(
        default=None,
        description="If true, only rows that already have a review (review_id IS NOT NULL)",
    ),
) -> list[DirectorErrorTimelineRow]:
    """
    Inbox-style list from view `director_error_resolution_timeline`
    (error_reports + session_steps + optional error_report_reviews).
    """
    parts: list[str] = ["1"]
    params: dict = {"lim": limit, "off": offset}
    if status_filter:
        parts.append("report_status = :st")
        params["st"] = status_filter.strip()
    if pending_only:
        parts.append("review_id IS NULL")
    if reviewed_only:
        parts.append("review_id IS NOT NULL")
    where_sql = " AND ".join(parts)
    sql = _list_sql(where_sql)
    try:
        result = db.execute(text(sql), params)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=(
                "Cannot read error report inbox (missing view or DB error). "
                "Ensure director_error_resolution_timeline exists (see seed_director_demo.py). "
                f"Detail: {exc!r}"
            ),
        ) from exc
    return [DirectorErrorTimelineRow.model_validate(dict(r._mapping)) for r in result]


@router.post("/{report_id}/reviews", response_model=DirectorErrorTimelineRow)
def submit_error_report_review(
    db: DbSession,
    pharmacist: CurrentPharmacist,
    report_id: int,
    body: ErrorReportReviewCreate,
) -> DirectorErrorTimelineRow:
    """Record a pharmacist review for an error report and mark the report resolved."""
    if body.decision == "adjust_recognition" and not (body.agreed_herb_name or "").strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="agreed_herb_name is required when decision is adjust_recognition",
        )
    existing = db.execute(
        text("SELECT id FROM error_report_reviews WHERE error_report_id = :eid LIMIT 1"),
        {"eid": report_id},
    ).first()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="This error report already has a review",
        )
    er = db.execute(
        text("SELECT id FROM error_reports WHERE id = :id"),
        {"id": report_id},
    ).first()
    if er is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Error report not found")

    now = now_sqlite_text()
    db.execute(
        text(
            """
            INSERT INTO error_report_reviews (
                error_report_id, reviewer_pharmacist_id, decision, agreed_herb_name, comment, created_at
            ) VALUES (:eid, :pid, :dec, :agreed, :comment, :now)
            """
        ),
        {
            "eid": report_id,
            "pid": pharmacist.id,
            "dec": body.decision,
            "agreed": (body.agreed_herb_name or "").strip() or None,
            "comment": (body.comment or "").strip() or None,
            "now": now,
        },
    )
    # If reviewer manually adjusted recognition in inbox, update session step immediately.
    if body.decision == "adjust_recognition":
        db.execute(
            text(
                """
                UPDATE session_steps
                SET
                    llm_recognized_name = :agreed,
                    match_status = CASE
                        WHEN :agreed = expected_herb_name THEN 'correct'
                        ELSE 'needs_review'
                    END,
                    reviewer_comment = TRIM(
                        COALESCE(reviewer_comment, '') ||
                        CASE WHEN COALESCE(reviewer_comment, '') = '' THEN '' ELSE ' ; ' END ||
                        :op
                    ),
                    updated_at = :now
                WHERE id = (SELECT step_id FROM error_reports WHERE id = :eid)
                """
            ),
            {
                "agreed": (body.agreed_herb_name or "").strip(),
                "op": f"inbox_adjust report_id={report_id} pharmacist_id={pharmacist.id}",
                "now": now,
                "eid": report_id,
            },
        )
    db.execute(
        text("UPDATE error_reports SET status = 'resolved' WHERE id = :id"),
        {"id": report_id},
    )
    rsid_row = db.execute(
        text("SELECT session_id FROM error_reports WHERE id = :id"),
        {"id": report_id},
    ).first()
    session_id = str(rsid_row._mapping["session_id"]) if rsid_row is not None else None
    if session_id:
        unresolved = db.execute(
            text("SELECT COUNT(*) AS c FROM error_reports WHERE session_id = :sid AND status != 'resolved'"),
            {"sid": session_id},
        ).scalar_one()
        if int(unresolved or 0) == 0:
            # All report decisions are ready, do final pass/return decision.
            confirmed = db.execute(
                text(
                    """
                    SELECT er.step_id
                    FROM error_reports er
                    JOIN error_report_reviews rv ON rv.error_report_id = er.id
                    WHERE er.session_id = :sid
                      AND rv.decision = 'confirm_error'
                    """
                ),
                {"sid": session_id},
            ).fetchall()
            if confirmed:
                source = db.execute(
                    text("SELECT id, prescription_id, llm_model_name FROM review_sessions WHERE id = :sid"),
                    {"sid": session_id},
                ).first()
                if source is not None:
                    issue_ids = {int(r._mapping["step_id"]) for r in confirmed}
                    from app.models import ReviewSession  # local import to avoid circulars
                    from sqlalchemy import select
                    from sqlalchemy.orm import selectinload

                    source_model = db.scalar(
                        select(ReviewSession)
                        .where(ReviewSession.id == session_id)
                        .options(selectinload(ReviewSession.steps), selectinload(ReviewSession.prescription)),
                    )
                    if source_model is not None:
                        selected_models = [s for s in source_model.steps if s.id in issue_ids]
                        if not selected_models:
                            selected_models = [
                                s for s in source_model.steps if s.match_status in {"incorrect", "needs_review", "pending"}
                            ]
                        if not selected_models:
                            selected_models = source_model.steps
                        source_model.status = "cancelled"
                        source_model.updated_at = now_sqlite_text()
                        db.add(source_model)
                        issue_indexes = ",".join(str(s.step_index) for s in selected_models)
                        new_s = create_session_with_specific_steps(
                            db,
                            source_session=source_model,
                            pharmacist_id=pharmacist.id,
                            selected_steps=selected_models,
                            notes=f"auto_return_from_error_inbox;parent_session_id={source_model.id};issue_step_indexes={issue_indexes}",
                        )
                        _append_audit_log(
                            db,
                            action="error_inbox_auto_return",
                            session_id=source_model.id,
                            pharmacist_id=pharmacist.id,
                            detail=f"new_session_id={new_s.id};issue_steps={issue_indexes}",
                        )
            else:
                # All reports resolved and none confirmed as true mismatch -> pass this session.
                db.execute(
                    text(
                        """
                        UPDATE review_sessions
                        SET status = 'completed', updated_at = :now
                        WHERE id = :sid
                        """
                    ),
                    {"sid": session_id, "now": now_sqlite_text()},
                )
                db.execute(
                    text(
                        """
                        UPDATE prescriptions
                        SET reviewing_doctor = :doctor
                        WHERE id = (
                            SELECT prescription_id FROM review_sessions WHERE id = :sid
                        )
                        """
                    ),
                    {"doctor": pharmacist.full_name, "sid": session_id},
                )
                _append_audit_log(
                    db,
                    action="error_inbox_auto_pass",
                    session_id=session_id,
                    pharmacist_id=pharmacist.id,
                    detail=f"reviewing_doctor={pharmacist.full_name}",
                )
    db.commit()

    row = db.execute(
        text("SELECT * FROM director_error_resolution_timeline WHERE error_report_id = :id LIMIT 1"),
        {"id": report_id},
    ).first()
    if row is None:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Review saved but timeline row not found",
        )
    return DirectorErrorTimelineRow.model_validate(dict(row._mapping))
