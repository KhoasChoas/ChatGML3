"""
从 E:\\Other\\Work\\Excel\\ 下的三个 xlsx 导入到 SQLite（见 schema.sql）。

用法:
  python import_from_excel.py
  python import_from_excel.py --excel-dir "D:/data/Excel" --db ../data/herb_review.db

依赖: pandas, openpyxl
"""

from __future__ import annotations

import argparse
import re
import sqlite3
import sys
import uuid
from collections import OrderedDict, defaultdict
from datetime import datetime
from pathlib import Path

import pandas as pd


def _project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def _load_schema(conn: sqlite3.Connection, schema_path: Path) -> None:
    sql = schema_path.read_text(encoding="utf-8")
    conn.executescript(sql)


def _normalize_str(v) -> str | None:
    if v is None or (isinstance(v, float) and pd.isna(v)):
        return None
    s = str(v).strip()
    return s if s else None


def _normalize_int(v) -> int | None:
    if v is None or (isinstance(v, float) and pd.isna(v)):
        return None
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return None


def _normalize_float(v) -> float | None:
    if v is None or (isinstance(v, float) and pd.isna(v)):
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def _is_department_director(job_title: str | None) -> int:
    if not job_title:
        return 0
    t = str(job_title).strip()
    if "药剂科主任" in t:
        return 1
    if re.search(r"科主任", t):
        return 1
    return 0


def discover_excel_files(excel_dir: Path) -> tuple[Path, Path, Path]:
    files = sorted(excel_dir.glob("*.xlsx"))
    if len(files) < 3:
        raise FileNotFoundError(f"xlsx 数量不足 3: {excel_dir}")

    herb_path = next((p for p in files if "herb_info" in p.name.lower()), None)
    if herb_path is None:
        xl = pd.ExcelFile(files[0])
        if "herb_info" in xl.sheet_names:
            herb_path = files[0]
    if herb_path is None:
        raise FileNotFoundError("未找到中草药信息表（文件名含 herb_info 或含 herb_info 工作表）")

    pharmacist_path = None
    prescription_path = None
    for p in files:
        if p.resolve() == herb_path.resolve():
            continue
        df0 = pd.read_excel(p, sheet_name=0, header=None, nrows=3)
        row0 = [str(x).strip() if pd.notna(x) else "" for x in df0.iloc[0].tolist()]
        joined = " ".join(row0)
        if "处方编号" in joined and "序号" in joined:
            prescription_path = p
        elif "工号" in joined and "姓名" in joined:
            pharmacist_path = p

    if pharmacist_path is None or prescription_path is None:
        leftovers = [p for p in files if p.resolve() != herb_path.resolve()]
        raise FileNotFoundError(
            "无法自动区分「药师表」与「处方表」。请检查表头是否包含：处方编号 / 工号+姓名。\n"
            f"当前目录文件: {[p.name for p in files]}"
        )

    return herb_path, pharmacist_path, prescription_path


def import_herb_catalog(conn: sqlite3.Connection, path: Path) -> int:
    df = pd.read_excel(path, sheet_name="herb_info", header=0)
    cols = {c: str(c).strip().replace("\n", "") for c in df.columns}
    df = df.rename(columns=cols)
    expected = [
        "药品代码",
        "中草药名称",
        "饮片性状",
        "炮制方法",
        "性味归经",
        "功能主治",
        "用法用量",
        "主要有效化学成分",
        "注意事项",
    ]
    missing = [c for c in expected if c not in df.columns]
    if missing:
        raise ValueError(f"中草药信息表缺少列: {missing}，实际: {list(df.columns)}")

    cur = conn.cursor()
    cur.execute("DELETE FROM herb_catalog")
    rows_by_code: dict[str, tuple] = {}
    for _, r in df.iterrows():
        code = _normalize_str(r.get("药品代码"))
        name = _normalize_str(r.get("中草药名称"))
        if not code or not name:
            continue
        rows_by_code[code] = (
            code,
            name,
            _normalize_str(r.get("饮片性状")),
            _normalize_str(r.get("炮制方法")),
            _normalize_str(r.get("性味归经")),
            _normalize_str(r.get("功能主治")),
            _normalize_str(r.get("用法用量")),
            _normalize_str(r.get("主要有效化学成分")),
            _normalize_str(r.get("注意事项")),
        )
    rows = list(rows_by_code.values())
    cur.executemany(
        """
        INSERT INTO herb_catalog (
            drug_code, herb_name, slice_traits, processing_method,
            nature_flavor_meridian, functions_indications, usage_dosage,
            main_chemistry, precautions
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    return len(rows)


def import_pharmacists(conn: sqlite3.Connection, path: Path) -> int:
    df = pd.read_excel(path, sheet_name=0, header=0)
    cols = {c: str(c).strip().replace("\n", "") for c in df.columns}
    df = df.rename(columns=cols)
    mapping = {
        "序号": "seq_no",
        "姓名": "full_name",
        "工号": "employee_id",
        "性别": "gender",
        "电话号码": "phone",
        "职称": "title_rank",
        "职务": "job_title",
        "科室": "department",
        "密码": "password_credential",
    }
    for cn in mapping:
        if cn not in df.columns:
            raise ValueError(f"药师表缺少列 {cn}，实际: {list(df.columns)}")

    cur = conn.cursor()
    cur.execute("DELETE FROM pharmacists")
    n = 0
    for _, r in df.iterrows():
        eid = _normalize_str(r.get("工号"))
        name = _normalize_str(r.get("姓名"))
        if not eid or not name:
            continue
        job = _normalize_str(r.get("职务"))
        cur.execute(
            """
            INSERT INTO pharmacists (
                seq_no, full_name, employee_id, gender, phone, title_rank,
                job_title, department, password_credential, is_department_director
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                _normalize_int(r.get("序号")),
                name,
                eid,
                _normalize_str(r.get("性别")),
                _normalize_str(r.get("电话号码")),
                _normalize_str(r.get("职称")),
                job,
                _normalize_str(r.get("科室")),
                _normalize_str(r.get("密码")),
                _is_department_director(job),
            ),
        )
        n += 1
    return n


def parse_prescriptions_from_excel(path: Path) -> tuple[list[dict], list[dict]]:
    """
    返回 (prescription_headers, line_items)。
    line_items: {prescription_no, line_no, herb_name, dosage, usage_method}
    """
    df = pd.read_excel(path, sheet_name=0, header=None)
    if df.shape[0] < 3:
        return [], []

    headers: list[dict] = []
    items: list[dict] = []

    current_no: str | None = None
    line_no = 0

    def flush_header(h: dict | None) -> None:
        if h and h.get("prescription_no"):
            headers.append(h)

    active: dict | None = None

    for i in range(2, len(df)):
        row = df.iloc[i]
        rx_no = _normalize_str(row[1])

        if rx_no is not None:
            if active is not None:
                flush_header(active)
            line_no = 0
            active = {
                "source_seq_no": _normalize_int(row[0]),
                "prescription_no": rx_no,
                "patient_name": _normalize_str(row[2]),
                "patient_gender": _normalize_str(row[3]),
                "patient_age": _normalize_str(row[4]),
                "fee_type": _normalize_str(row[5]),
                "medical_record_no": _normalize_str(row[6]),
                "dept_bed": _normalize_str(row[7]),
                "address": _normalize_str(row[8]),
                "phone": _normalize_str(row[9]),
                "diagnosis": _normalize_str(row[10]),
                "prescribed_at": _normalize_str(row[11]),
                "herb_kind_count": _normalize_int(row[12]),
                "drug_fee": _normalize_float(row[16]),
                "injection_fee": _normalize_float(row[17]),
                "prescribing_doctor": _normalize_str(row[18]),
                "dispensing_pharmacist": _normalize_str(row[19]),
                "reviewing_doctor": _normalize_str(row[20]),
            }

        herb = _normalize_str(row[13])
        if herb and active is not None:
            line_no += 1
            items.append(
                {
                    "prescription_no": active["prescription_no"],
                    "line_no": line_no,
                    "herb_name": herb,
                    "dosage": _normalize_str(row[14]),
                    "usage_method": _normalize_str(row[15]),
                }
            )

    if active is not None:
        flush_header(active)

    return headers, items


def import_prescriptions(conn: sqlite3.Connection, path: Path) -> tuple[int, int]:
    headers, items = parse_prescriptions_from_excel(path)
    header_by_no = OrderedDict()
    for h in headers:
        if h.get("prescription_no"):
            header_by_no[str(h["prescription_no"])] = h
    headers = list(header_by_no.values())

    grouped: dict[str, list[dict]] = defaultdict(list)
    for it in items:
        grouped[str(it["prescription_no"])].append(it)
    normalized_items: list[dict] = []
    for rx_no, lst in grouped.items():
        for i, it in enumerate(lst, start=1):
            normalized_items.append({**it, "line_no": i})

    cur = conn.cursor()
    cur.execute("DELETE FROM prescription_items")
    cur.execute("DELETE FROM prescriptions")

    rx_id_by_no: dict[str, int] = {}
    for h in headers:
        cur.execute(
            """
            INSERT INTO prescriptions (
                source_seq_no, prescription_no, patient_name, patient_gender, patient_age,
                fee_type, medical_record_no, dept_bed, address, phone, diagnosis,
                prescribed_at, herb_kind_count, drug_fee, injection_fee,
                prescribing_doctor, dispensing_pharmacist, reviewing_doctor
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                h["source_seq_no"],
                h["prescription_no"],
                h["patient_name"],
                h["patient_gender"],
                h["patient_age"],
                h["fee_type"],
                h["medical_record_no"],
                h["dept_bed"],
                h["address"],
                h["phone"],
                h["diagnosis"],
                h["prescribed_at"],
                h["herb_kind_count"],
                h["drug_fee"],
                h["injection_fee"],
                h["prescribing_doctor"],
                h["dispensing_pharmacist"],
                h["reviewing_doctor"],
            ),
        )
        rx_id_by_no[h["prescription_no"]] = int(cur.lastrowid)

    n_items = 0
    for it in normalized_items:
        rid = rx_id_by_no.get(it["prescription_no"])
        if rid is None:
            continue
        cur.execute(
            """
            INSERT INTO prescription_items (prescription_id, line_no, herb_name, dosage, usage_method)
            VALUES (?, ?, ?, ?, ?)
            """,
            (rid, it["line_no"], it["herb_name"], it["dosage"], it["usage_method"]),
        )
        n_items += 1

    return len(headers), n_items


def seed_demo_session(conn: sqlite3.Connection) -> None:
    """插入一条占位复核会话，便于验收视图与前端联调（无图片、无 LLM）。"""
    cur = conn.cursor()
    cur.execute("SELECT id FROM pharmacists ORDER BY id LIMIT 1")
    row = cur.fetchone()
    if not row:
        return
    pid = row[0]
    cur.execute("SELECT id, prescription_no FROM prescriptions ORDER BY id LIMIT 1")
    rx = cur.fetchone()
    if not rx:
        return
    rx_id, rx_no = int(rx[0]), str(rx[1])
    cur.execute("SELECT id FROM review_sessions WHERE prescription_id = ?", (rx_id,))
    if cur.fetchone():
        return

    sid = str(uuid.uuid4())
    cur.execute(
        """
        INSERT INTO review_sessions (id, prescription_id, created_by_pharmacist_id, status, llm_model_name, notes)
        VALUES (?, ?, ?, 'completed', 'ChatGLM3', ?)
        """,
        (sid, rx_id, pid, "导入后自动生成的演示会话（可删除）"),
    )
    cur.execute(
        """
        SELECT id, line_no, herb_name FROM prescription_items
        WHERE prescription_id = ? ORDER BY line_no LIMIT 3
        """,
        (rx_id,),
    )
    steps = cur.fetchall()
    for idx, (item_id, _ln, name) in enumerate(steps, start=1):
        cur.execute(
            """
            INSERT INTO session_steps (
                session_id, step_index, prescription_item_id, expected_herb_name,
                match_status, llm_recognized_name
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                sid,
                idx,
                int(item_id),
                str(name),
                "correct" if idx % 2 == 1 else "incorrect",
                str(name) if idx % 2 == 1 else "（演示）识别偏差",
            ),
        )
    if steps:
        cur.execute(
            "SELECT id FROM session_steps WHERE session_id = ? AND match_status = 'incorrect' LIMIT 1",
            (sid,),
        )
        bad = cur.fetchone()
        if bad:
            cur.execute(
                """
                INSERT INTO error_reports (session_id, step_id, reported_by_pharmacist_id, description, status)
                VALUES (?, ?, ?, ?, 'notified')
                """,
                (sid, int(bad[0]), pid, "演示：对错误识别上报"),
            )
            er_id = int(cur.lastrowid)
            cur.execute(
                """
                INSERT INTO error_report_reviews (
                    error_report_id, reviewer_pharmacist_id, decision, agreed_herb_name, comment
                ) VALUES (?, ?, 'adjust_recognition', ?, ?)
                """,
                (er_id, pid, steps[0][2], "演示：复核采纳药名"),
            )


def main(argv: list[str]) -> int:
    default_excel = Path(r"E:\Other\Work\Excel")
    default_db = _project_root() / "data" / "herb_review.db"

    ap = argparse.ArgumentParser(description="从 Excel 导入中草药复核 SQLite 库")
    ap.add_argument("--excel-dir", type=Path, default=default_excel)
    ap.add_argument("--db", type=Path, default=default_db)
    ap.add_argument("--no-demo-session", action="store_true")
    args = ap.parse_args(argv)

    excel_dir: Path = args.excel_dir
    db_path: Path = args.db
    db_path.parent.mkdir(parents=True, exist_ok=True)

    herb_xlsx, ph_xlsx, rx_xlsx = discover_excel_files(excel_dir)

    schema_path = Path(__file__).with_name("schema.sql")
    conn = sqlite3.connect(db_path)
    try:
        _load_schema(conn, schema_path)
        n_herb = import_herb_catalog(conn, herb_xlsx)
        n_ph = import_pharmacists(conn, ph_xlsx)
        n_rx, n_lines = import_prescriptions(conn, rx_xlsx)
        if not args.no_demo_session:
            seed_demo_session(conn)
        conn.execute(
            "INSERT OR REPLACE INTO app_meta(key, value) VALUES (?, ?)",
            ("last_import_at", datetime.now().isoformat(timespec="seconds")),
        )
        conn.commit()
    finally:
        conn.close()

    print("导入完成:")
    print(f"  数据库: {db_path}")
    print(f"  中草药: {n_herb} 条 ← {herb_xlsx.name}")
    print(f"  药师:   {n_ph} 条 ← {ph_xlsx.name}")
    print(f"  处方:   {n_rx} 张, 明细 {n_lines} 行 ← {rx_xlsx.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
