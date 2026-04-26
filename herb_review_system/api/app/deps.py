from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import Pharmacist
from app.security import decode_access_token


def get_current_pharmacist_id(
    authorization: Annotated[str | None, Header(alias="Authorization")] = None,
    x_dev_pharmacist_id: Annotated[str | None, Header(alias="X-Dev-Pharmacist-Id")] = None,
) -> int:
    if settings.dev_allow_header_auth and x_dev_pharmacist_id:
        try:
            return int(x_dev_pharmacist_id)
        except ValueError:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid X-Dev-Pharmacist-Id")

    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token")

    token = authorization.split(" ", 1)[1].strip()
    sub = decode_access_token(token)
    if sub is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token")

    try:
        return int(sub)
    except ValueError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token subject")


def get_current_pharmacist(
    db: Annotated[Session, Depends(get_db)],
    pharmacist_id: Annotated[int, Depends(get_current_pharmacist_id)],
) -> Pharmacist:
    p = db.get(Pharmacist, pharmacist_id)
    if p is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Pharmacist not found")
    return p


DbSession = Annotated[Session, Depends(get_db)]
CurrentPharmacist = Annotated[Pharmacist, Depends(get_current_pharmacist)]
CurrentPharmacistId = Annotated[int, Depends(get_current_pharmacist_id)]
