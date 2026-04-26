from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query, status
from sqlalchemy import func, or_, select
from sqlalchemy.orm import selectinload

from app.deps import DbSession
from app.models import Prescription
from app.schemas.common import PageMeta
from app.schemas.prescription import PrescriptionDetail, PrescriptionItemOut, PrescriptionListItem, PrescriptionPage

router = APIRouter()


@router.get("", response_model=PrescriptionPage)
def list_prescriptions(
    db: DbSession,
    q: str | None = Query(default=None, description="Search prescription_no / patient_name / diagnosis"),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> PrescriptionPage:
    stmt = select(Prescription)
    count_stmt = select(func.count()).select_from(Prescription)

    if q:
        pattern = f"%{q.strip()}%"
        filt = or_(
            Prescription.prescription_no.like(pattern),
            Prescription.patient_name.like(pattern),
            Prescription.diagnosis.like(pattern),
        )
        stmt = stmt.where(filt)
        count_stmt = count_stmt.where(filt)

    total = int(db.scalar(count_stmt) or 0)
    rows = db.scalars(stmt.order_by(Prescription.id.desc()).offset(offset).limit(limit)).all()

    return PrescriptionPage(
        items=[PrescriptionListItem.model_validate(r) for r in rows],
        meta=PageMeta(total=total, limit=limit, offset=offset),
    )


@router.get("/{prescription_id}", response_model=PrescriptionDetail)
def get_prescription(db: DbSession, prescription_id: int) -> PrescriptionDetail:
    rx = db.scalar(
        select(Prescription)
        .where(Prescription.id == prescription_id)
        .options(selectinload(Prescription.items)),
    )
    if rx is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Prescription not found")

    return PrescriptionDetail(
        id=rx.id,
        prescription_no=rx.prescription_no,
        patient_name=rx.patient_name,
        diagnosis=rx.diagnosis,
        prescribed_at=rx.prescribed_at,
        herb_kind_count=rx.herb_kind_count,
        items=[PrescriptionItemOut.model_validate(i) for i in rx.items],
    )
