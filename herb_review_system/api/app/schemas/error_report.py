from __future__ import annotations

from typing import Literal

from pydantic import Field

from app.schemas.common import ConfigModel


class ErrorReportReviewCreate(ConfigModel):
    decision: Literal["confirm_error", "reject_error", "adjust_recognition"]
    agreed_herb_name: str | None = Field(default=None, description="Required when decision is adjust_recognition")
    comment: str | None = None
