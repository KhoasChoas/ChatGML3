from __future__ import annotations

from app.schemas.common import ConfigModel


class PharmacistBrief(ConfigModel):
    id: int
    employee_id: str
    full_name: str
    department: str | None = None
    job_title: str | None = None
    is_department_director: int = 0
