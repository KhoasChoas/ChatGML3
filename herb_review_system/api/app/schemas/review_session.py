from __future__ import annotations

from pydantic import Field

from app.schemas.common import ConfigModel


class SessionStepOut(ConfigModel):
    id: int
    step_index: int
    prescription_item_id: int | None = None
    expected_herb_name: str
    image_uri: str | None = None
    llm_recognized_name: str | None = None
    llm_confidence: float | None = None
    match_status: str
    reviewer_comment: str | None = None


class ReviewSessionOut(ConfigModel):
    id: str
    prescription_id: int
    prescription_no: str | None = None
    created_by_pharmacist_id: int
    status: str
    llm_model_name: str | None = None
    notes: str | None = None
    created_at: str
    updated_at: str
    steps: list[SessionStepOut] = Field(default_factory=list)


class ReviewSessionCreate(ConfigModel):
    prescription_id: int
    notes: str | None = None
    status: str = "in_progress"
    llm_model_name: str | None = "ChatGLM3"


class SessionStepPatch(ConfigModel):
    image_uri: str | None = None
    llm_recognized_name: str | None = None
    llm_confidence: float | None = None
    llm_raw_response: str | None = None
    match_status: str | None = None
    reviewer_comment: str | None = None


class SessionStatusPatch(ConfigModel):
    status: str


class SessionStepErrorReportCreate(ConfigModel):
    description: str | None = None


class ErrorReportCreated(ConfigModel):
    error_report_id: int
    status: str
