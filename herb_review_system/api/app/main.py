from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import build_api_v1_router

app = FastAPI(
    title="Herb Review API",
    version="1.0.0",
    root_path=settings.api_root_path or "",
)

if settings.cors_origin_list == ["*"]:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )
else:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(build_api_v1_router(), prefix=settings.api_v1_prefix)


@app.get("/health", tags=["health"])
def root_health() -> dict[str, str]:
    return {"status": "ok"}
