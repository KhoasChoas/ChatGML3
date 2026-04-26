from __future__ import annotations

from pydantic import Field

from app.schemas.common import ConfigModel, PageMeta


class PrescriptionItemOut(ConfigModel):
    id: int
    line_no: int
    herb_name: str
    dosage: str | None = None
    usage_method: str | None = None


class PrescriptionListItem(ConfigModel):
    id: int
    prescription_no: str
    patient_name: str | None = None
    diagnosis: str | None = None
    prescribed_at: str | None = None
    herb_kind_count: int | None = None


class PrescriptionDetail(PrescriptionListItem):
    items: list[PrescriptionItemOut] = Field(default_factory=list)


class PrescriptionPage(ConfigModel):
    items: list[PrescriptionListItem]
    meta: PageMeta
