from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.database import engine, get_db

router = APIRouter()


@router.get("/diagnostics/integration", summary="自检：数据库与科主任视图是否可用")
def integration_check(db: Session = Depends(get_db)) -> dict:
    steps: list[dict[str, str | bool]] = []

    try:
        db.execute(text("SELECT 1"))
        url = str(engine.url)
        if "@" in url:
            url = url.split("@", 1)[-1]
        steps.append({"step": "database", "ok": True, "detail": url})
    except Exception as e:  # noqa: BLE001
        steps.append({"step": "database", "ok": False, "detail": str(e)})

    try:
        n = db.scalar(text("SELECT COUNT(*) FROM director_work_overview"))
        steps.append({"step": "view director_work_overview", "ok": True, "detail": f"rows={int(n or 0)}"})
    except Exception as e:  # noqa: BLE001
        steps.append({"step": "view director_work_overview", "ok": False, "detail": str(e)})

    return {"ok": all(bool(s.get("ok")) for s in steps), "steps": steps}
