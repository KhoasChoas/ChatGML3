from __future__ import annotations

from fastapi import APIRouter
from sqlalchemy import select

from app.deps import DbSession
from app.models import Pharmacist
from app.schemas.pharmacist import PharmacistBrief

router = APIRouter()


@router.get("", response_model=list[PharmacistBrief], summary="List pharmacists (local dev picker)")
def list_pharmacists(db: DbSession) -> list[PharmacistBrief]:
    rows = db.scalars(select(Pharmacist).order_by(Pharmacist.id)).all()
    return [PharmacistBrief.model_validate(r) for r in rows]
