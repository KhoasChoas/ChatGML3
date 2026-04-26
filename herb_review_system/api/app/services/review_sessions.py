from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.models import Prescription, ReviewSession, SessionStep
from app.util_time import now_sqlite_text


def create_session_with_steps(
    db: Session,
    *,
    prescription_id: int,
    pharmacist_id: int,
    status: str,
    llm_model_name: str | None,
    notes: str | None,
) -> ReviewSession:
    rx = db.scalar(
        select(Prescription).where(Prescription.id == prescription_id).options(selectinload(Prescription.items)),
    )
    if rx is None:
        raise ValueError("prescription_not_found")

    now = now_sqlite_text()
    sid = str(uuid.uuid4())

    session = ReviewSession(
        id=sid,
        prescription_id=prescription_id,
        created_by_pharmacist_id=pharmacist_id,
        status=status,
        llm_model_name=llm_model_name or "ChatGLM3",
        notes=notes,
        created_at=now,
        updated_at=now,
    )
    db.add(session)
    db.flush()

    items = sorted(rx.items, key=lambda it: it.line_no)
    for idx, it in enumerate(items, start=1):
        db.add(
            SessionStep(
                session_id=sid,
                step_index=idx,
                prescription_item_id=it.id,
                expected_herb_name=it.herb_name,
                image_uri=None,
                llm_recognized_name=None,
                llm_confidence=None,
                llm_raw_response=None,
                match_status="pending",
                reviewer_comment=None,
                created_at=now,
                updated_at=now,
            )
        )

    db.commit()
    db.refresh(session)
    return db.scalar(
        select(ReviewSession)
        .where(ReviewSession.id == sid)
        .options(
            selectinload(ReviewSession.steps),
            selectinload(ReviewSession.prescription),
        ),
    )  # type: ignore[return-value]


def create_session_with_specific_steps(
    db: Session,
    *,
    source_session: ReviewSession,
    pharmacist_id: int,
    selected_steps: list[SessionStep],
    notes: str | None = None,
) -> ReviewSession:
    if not selected_steps:
        raise ValueError("no_steps_selected")

    now = now_sqlite_text()
    sid = str(uuid.uuid4())
    session = ReviewSession(
        id=sid,
        prescription_id=source_session.prescription_id,
        created_by_pharmacist_id=pharmacist_id,
        status="in_progress",
        llm_model_name=source_session.llm_model_name or "ChatGLM3",
        notes=notes or f"返工复核来源会话 {source_session.id}",
        created_at=now,
        updated_at=now,
    )
    db.add(session)
    db.flush()

    ordered = sorted(selected_steps, key=lambda it: it.step_index)
    for idx, old in enumerate(ordered, start=1):
        db.add(
            SessionStep(
                session_id=sid,
                step_index=idx,
                prescription_item_id=old.prescription_item_id,
                expected_herb_name=old.expected_herb_name,
                image_uri=None,
                llm_recognized_name=None,
                llm_confidence=None,
                llm_raw_response=None,
                match_status="pending",
                reviewer_comment=f"返工复核：来源步骤 {old.step_index}",
                created_at=now,
                updated_at=now,
            ),
        )

    db.commit()
    return db.scalar(
        select(ReviewSession)
        .where(ReviewSession.id == sid)
        .options(
            selectinload(ReviewSession.steps),
            selectinload(ReviewSession.prescription),
        ),
    )  # type: ignore[return-value]
