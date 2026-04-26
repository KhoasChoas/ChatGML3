from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.deps import CurrentPharmacist, DbSession
from app.models import Pharmacist
from app.schemas.auth import LoginRequest, MeResponse, TokenResponse
from app.schemas.pharmacist import PharmacistBrief
from app.security import create_access_token

router = APIRouter()


@router.post("/login", response_model=TokenResponse)
def login(db: DbSession, body: LoginRequest) -> TokenResponse:
    p = db.scalar(select(Pharmacist).where(Pharmacist.employee_id == body.employee_id))
    if p is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Unknown employee_id")

    if p.password_credential is None or p.password_credential != body.password:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid password")

    token = create_access_token(str(p.id))
    return TokenResponse(access_token=token)


@router.get("/me", response_model=MeResponse)
def me(pharmacist: CurrentPharmacist) -> MeResponse:
    return MeResponse(pharmacist=PharmacistBrief.model_validate(pharmacist))
