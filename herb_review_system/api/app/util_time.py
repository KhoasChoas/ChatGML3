from __future__ import annotations

import datetime as dt


def now_sqlite_text() -> str:
    return dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
