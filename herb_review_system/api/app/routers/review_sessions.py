from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import select, text
from sqlalchemy.orm import selectinload

from app.deps import CurrentPharmacist, CurrentPharmacistId, DbSession
from app.models import ReviewSession, SessionStep
from app.schemas.review_session import (
    ErrorReportCreated,
    ReviewSessionCreate,
    ReviewSessionOut,
    SessionStatusPatch,
    SessionStepErrorReportCreate,
    SessionStepOut,
    SessionStepPatch,
)
from app.services.review_sessions import create_session_with_specific_steps, create_session_with_steps
from app.util_time import now_sqlite_text

router = APIRouter()


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


@router.post("", response_model=ReviewSessionOut)
def create_review_session(
    db: DbSession,
    pharmacist_id: CurrentPharmacistId,
    body: ReviewSessionCreate,
) -> ReviewSessionOut:
    try:
        session = create_session_with_steps(
            db,
            prescription_id=body.prescription_id,
            pharmacist_id=pharmacist_id,
            status=body.status,
            llm_model_name=body.llm_model_name,
            notes=body.notes,
        )
    except ValueError as e:
        if str(e) == "prescription_not_found":
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Prescription not found") from e
        raise

    return ReviewSessionOut.model_validate(session)


@router.get("/{session_id}", response_model=ReviewSessionOut)
def get_review_session(db: DbSession, session_id: str) -> ReviewSessionOut:
    s = db.scalar(
        select(ReviewSession)
        .where(ReviewSession.id == session_id)
        .options(
            selectinload(ReviewSession.steps),
            selectinload(ReviewSession.prescription),
        ),
    )
    if s is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")
    return ReviewSessionOut.model_validate(s)


@router.get("", response_model=list[ReviewSessionOut])
def list_review_sessions(
    db: DbSession,
    pharmacist_id: CurrentPharmacistId,
    prescription_id: int | None = Query(default=None),
    mine: bool = Query(
        default=False,
        description="If true, only sessions created by the authenticated pharmacist.",
    ),
    limit: int = Query(default=50, ge=1, le=200),
) -> list[ReviewSessionOut]:
    stmt = (
        select(ReviewSession)
        .options(
            selectinload(ReviewSession.steps),
            selectinload(ReviewSession.prescription),
        )
        .order_by(ReviewSession.created_at.desc())
    )
    if prescription_id is not None:
        stmt = stmt.where(ReviewSession.prescription_id == prescription_id)
    if mine:
        stmt = stmt.where(ReviewSession.created_by_pharmacist_id == pharmacist_id)
    rows = db.scalars(stmt.limit(limit)).all()
    return [ReviewSessionOut.model_validate(r) for r in rows]


@router.patch("/{session_id}/steps/{step_id}", response_model=SessionStepOut)
def patch_session_step(
    db: DbSession,
    _pharmacist_id: CurrentPharmacistId,
    session_id: str,
    step_id: int,
    body: SessionStepPatch,
) -> SessionStepOut:
    step = db.scalar(
        select(SessionStep).where(SessionStep.id == step_id, SessionStep.session_id == session_id),
    )
    if step is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Step not found")

    data = body.model_dump(exclude_unset=True)
    now = now_sqlite_text()
    for k, v in data.items():
        setattr(step, k, v)
    step.updated_at = now

    db.add(step)
    db.commit()
    db.refresh(step)
    return SessionStepOut.model_validate(step)


@router.post("/{session_id}/steps/{step_id}/error-report", response_model=ErrorReportCreated)
def report_session_step_error(
    db: DbSession,
    pharmacist: CurrentPharmacist,
    session_id: str,
    step_id: int,
    body: SessionStepErrorReportCreate,
) -> ErrorReportCreated:
    """Create an error_reports row for this session step (broadcast / inbox workflow)."""
    if not pharmacist.can_submit_error_report:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This pharmacist account is not allowed to submit error reports",
        )
    step = db.scalar(
        select(SessionStep).where(SessionStep.id == step_id, SessionStep.session_id == session_id),
    )
    if step is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Step not found for this session")

    dup = db.execute(
        text(
            "SELECT id FROM error_reports WHERE step_id = :sid AND status IN ('open', 'notified') LIMIT 1",
        ),
        {"sid": step_id},
    ).first()
    if dup is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="An open error report already exists for this step",
        )

    now = now_sqlite_text()
    desc = (body.description or "").strip() or None
    db.execute(
        text(
            """
            INSERT INTO error_reports (
                session_id, step_id, reported_by_pharmacist_id, description, status, created_at
            ) VALUES (:rsid, :stid, :pid, :desc, 'open', :now)
            """
        ),
        {
            "rsid": session_id,
            "stid": step_id,
            "pid": pharmacist.id,
            "desc": desc,
            "now": now,
        },
    )
    # Pure report path: suspend current session and wait inbox decisions.
    db.execute(
        text(
            """
            UPDATE review_sessions
            SET
                status = 'draft',
                notes = TRIM(
                    COALESCE(notes, '') ||
                    CASE WHEN COALESCE(notes, '') = '' THEN '' ELSE ' ; ' END ||
                    :tag
                ),
                updated_at = :now
            WHERE id = :sid
            """
        ),
        {
            "sid": session_id,
            "tag": f"suspended_pending_error=1;latest_error_step={step.step_index}",
            "now": now,
        },
    )
    _append_audit_log(
        db,
        action="review_suspended_by_error_report",
        session_id=session_id,
        pharmacist_id=pharmacist.id,
        detail=f"step_id={step_id}",
    )
    db.commit()
    rid = db.execute(text("SELECT last_insert_rowid()")).scalar_one()
    return ErrorReportCreated(error_report_id=int(rid), status="open")


@router.patch("/{session_id}/status", response_model=ReviewSessionOut)
def patch_session_status(
    db: DbSession,
    pharmacist: CurrentPharmacist,
    session_id: str,
    body: SessionStatusPatch,
) -> ReviewSessionOut:
    s = db.scalar(
        select(ReviewSession)
        .where(ReviewSession.id == session_id)
        .options(
            selectinload(ReviewSession.steps),
            selectinload(ReviewSession.prescription),
        ),
    )
    if s is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    allowed = {"draft", "in_progress", "completed", "cancelled"}
    if body.status not in allowed:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"status must be one of {sorted(allowed)}")

    s.status = body.status
    if body.status == "completed":
        all_correct = all(step.match_status == "correct" for step in s.steps) and len(s.steps) > 0
        if all_correct:
            rx = s.prescription
            if rx is not None:
                rx.reviewing_doctor = pharmacist.full_name
                db.add(rx)
                _append_audit_log(
                    db,
                    action="review_completed",
                    session_id=s.id,
                    pharmacist_id=pharmacist.id,
                    detail=f"prescription_id={rx.id};prescription_no={rx.prescription_no};reviewing_doctor={pharmacist.full_name}",
                )
    s.updated_at = now_sqlite_text()
    db.add(s)
    db.commit()
    db.refresh(s)
    return ReviewSessionOut.model_validate(s)


@router.post("/{session_id}/return", response_model=ReviewSessionOut)
def return_session_for_recheck(
    db: DbSession,
    pharmacist_id: CurrentPharmacistId,
    session_id: str,
) -> ReviewSessionOut:
    source = db.scalar(
        select(ReviewSession)
        .where(ReviewSession.id == session_id)
        .options(
            selectinload(ReviewSession.steps),
            selectinload(ReviewSession.prescription),
        ),
    )
    if source is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")

    issues = [s for s in source.steps if s.match_status in {"incorrect", "needs_review", "pending"}]
    if not issues:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="No problematic steps to return",
        )

    source.status = "cancelled"
    source.updated_at = now_sqlite_text()
    db.add(source)

    issue_indexes = ",".join(str(s.step_index) for s in issues)
    new_session = create_session_with_specific_steps(
        db,
        source_session=source,
        pharmacist_id=pharmacist_id,
        selected_steps=issues,
        notes=f"return_recheck;parent_session_id={source.id};issue_step_indexes={issue_indexes}",
    )
    _append_audit_log(
        db,
        action="review_returned",
        session_id=source.id,
        pharmacist_id=pharmacist_id,
        detail=f"new_session_id={new_session.id};issue_steps={issue_indexes}",
    )
    _append_audit_log(
        db,
        action="review_recheck_created",
        session_id=new_session.id,
        pharmacist_id=pharmacist_id,
        detail=f"parent_session_id={source.id};issue_steps={issue_indexes}",
    )
    db.commit()
    return ReviewSessionOut.model_validate(new_session)
