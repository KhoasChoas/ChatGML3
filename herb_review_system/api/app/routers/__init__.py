from fastapi import APIRouter

from app.routers import analytics, auth, diagnostics, error_reports, health, pharmacists, prescriptions, review_sessions


def build_api_v1_router() -> APIRouter:
    api = APIRouter()
    api.include_router(health.router, tags=["health"])
    api.include_router(diagnostics.router, tags=["diagnostics"])
    api.include_router(auth.router, prefix="/auth", tags=["auth"])
    api.include_router(pharmacists.router, prefix="/pharmacists", tags=["pharmacists"])
    api.include_router(prescriptions.router, prefix="/prescriptions", tags=["prescriptions"])
    api.include_router(review_sessions.router, prefix="/review-sessions", tags=["review-sessions"])
    api.include_router(analytics.router, prefix="/analytics", tags=["analytics"])
    api.include_router(error_reports.router, prefix="/error-reports", tags=["error-reports"])
    return api
