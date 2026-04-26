from __future__ import annotations

from fastapi import APIRouter

router = APIRouter()


@router.get("/health", summary="Liveness")
def health() -> dict[str, str]:
    return {"status": "ok"}
