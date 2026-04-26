from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class Message(BaseModel):
    detail: str = Field(..., description="Human-readable message")


class PageMeta(BaseModel):
    total: int
    limit: int
    offset: int


class ConfigModel(BaseModel):
    model_config = ConfigDict(from_attributes=True, populate_by_name=True)
