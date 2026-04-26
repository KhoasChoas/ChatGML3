from __future__ import annotations

from app.schemas.common import ConfigModel
from app.schemas.pharmacist import PharmacistBrief


class LoginRequest(ConfigModel):
    employee_id: str
    password: str


class TokenResponse(ConfigModel):
    access_token: str
    token_type: str = "bearer"


class MeResponse(ConfigModel):
    pharmacist: PharmacistBrief
