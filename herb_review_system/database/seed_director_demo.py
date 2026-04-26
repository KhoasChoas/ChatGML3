"""
基于当前 SQLite（已由 Excel 导入）生成多条「智能复核」模拟流水，
供药剂科主任「工作分析」预览与联调；并可生成 Android 端静态演示数据文件。

用法:
  python seed_director_demo.py
  python seed_director_demo.py --db ../data/herb_review.db --emit-android ../../herb_review_android/app/src/main/java/com/zhipu/herbreview/data/DirectorAnalyticsDemoData.kt

说明:
  - 仅删除 notes 以 SEED_TAG 开头的 review_sessions（级联删除步骤/报错/复核）。
  - 插入若干 completed 会话 + session_steps，部分含 error_reports + error_report_reviews。
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
import uuid
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path


SEED_TAG = "seed:director_demo_v3"


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _kotlin_escape(s: str | None) -> str:
    if s is None:
        return '""'
    s = str(s)
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def recreate_director_views(conn: sqlite3.Connection) -> None:
    """修正聚合视图，避免 steps × error_reports 笛卡尔积导致计数膨胀。"""
    conn.executescript(
        """
        DROP VIEW IF EXISTS director_error_resolution_timeline;
        DROP VIEW IF EXISTS director_work_overview;

        CREATE VIEW director_work_overview AS
        SELECT
            rs.id AS session_id,
            rs.created_at AS session_started_at,
            rs.status AS session_status,
            pr.prescription_no,
            pr.patient_name,
            pr.diagnosis,
            creator.full_name AS created_by_name,
            creator.employee_id AS created_by_employee_id,
            (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id) AS step_total,
            (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id AND ss.match_status = 'correct') AS step_correct,
            (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id AND ss.match_status = 'incorrect') AS step_incorrect,
            (SELECT COUNT(*) FROM session_steps ss WHERE ss.session_id = rs.id AND ss.match_status = 'needs_review') AS step_needs_review,
            (SELECT COUNT(*) FROM error_reports er WHERE er.session_id = rs.id) AS error_reports_filed
        FROM review_sessions rs
        JOIN prescriptions pr ON pr.id = rs.prescription_id
        JOIN pharmacists creator ON creator.id = rs.created_by_pharmacist_id;

        CREATE VIEW director_error_resolution_timeline AS
        SELECT
            er.id AS error_report_id,
            er.session_id AS session_id,
            er.description AS description,
            er.created_at AS reported_at,
            er.status AS report_status,
            ss.step_index,
            ss.expected_herb_name,
            ss.llm_recognized_name,
            reporter.full_name AS reported_by_name,
            pr.prescription_no,
            rr.id AS review_id,
            rr.created_at AS reviewed_at,
            rev.full_name AS reviewer_name,
            rr.decision,
            rr.agreed_herb_name,
            rr.comment AS review_comment
        FROM error_reports er
        JOIN session_steps ss ON ss.id = er.step_id
        JOIN review_sessions rs ON rs.id = er.session_id
        JOIN prescriptions pr ON pr.id = rs.prescription_id
        JOIN pharmacists reporter ON reporter.id = er.reported_by_pharmacist_id
        LEFT JOIN error_report_reviews rr ON rr.error_report_id = er.id
        LEFT JOIN pharmacists rev ON rev.id = rr.reviewer_pharmacist_id
        ORDER BY er.created_at DESC;
        """
    )
    conn.commit()


def clear_seed_sessions(conn: sqlite3.Connection) -> int:
    cur = conn.cursor()
    cur.execute(
        "SELECT id FROM review_sessions WHERE notes LIKE ?",
        (SEED_TAG + "%",),
    )
    ids = [r[0] for r in cur.fetchall()]
    for sid in ids:
        cur.execute("DELETE FROM review_sessions WHERE id = ?", (sid,))
    conn.commit()
    return len(ids)


def pick_pharmacists(conn: sqlite3.Connection) -> tuple[int, int, str, str]:
    cur = conn.cursor()
    cur.execute(
        "SELECT id, full_name FROM pharmacists WHERE is_department_director = 1 ORDER BY id LIMIT 1"
    )
    row = cur.fetchone()
    if not row:
        raise RuntimeError("数据库中未找到药剂科主任（is_department_director=1）")
    director_id, director_name = int(row[0]), str(row[1])
    cur.execute(
        "SELECT id, full_name FROM pharmacists WHERE id != ? ORDER BY id LIMIT 1",
        (director_id,),
    )
    row2 = cur.fetchone()
    if not row2:
        raise RuntimeError("数据库中至少需要 2 名药师（一名主任 + 一名普通药师）")
    other_id, other_name = int(row2[0]), str(row2[1])
    return director_id, other_id, director_name, other_name


def pick_prescriptions(conn: sqlite3.Connection, limit: int) -> list[tuple[int, str]]:
    cur = conn.cursor()
    cur.execute(
        """
        SELECT p.id, p.prescription_no
        FROM prescriptions p
        JOIN prescription_items i ON i.prescription_id = p.id
        GROUP BY p.id
        HAVING COUNT(*) >= 4
        ORDER BY p.id
        LIMIT ?
        """,
        (limit,),
    )
    rows = [(int(r[0]), str(r[1])) for r in cur.fetchall()]
    if len(rows) < 2:
        raise RuntimeError("需要至少 2 张含 4 味及以上药材的处方，请先执行 import_from_excel.py")
    return rows


def fetch_items(conn: sqlite3.Connection, prescription_id: int, max_n: int) -> list[tuple[int, str]]:
    cur = conn.cursor()
    cur.execute(
        """
        SELECT id, herb_name
        FROM prescription_items
        WHERE prescription_id = ?
        ORDER BY line_no
        LIMIT ?
        """,
        (prescription_id, max_n),
    )
    return [(int(r[0]), str(r[1])) for r in cur.fetchall()]


def _kotlin_str_or_null(v) -> str:
    if v is None:
        return "null"
    return _kotlin_escape(v)


def insert_session_with_flow(
    conn: sqlite3.Connection,
    *,
    session_id: str,
    prescription_id: int,
    creator_id: int,
    status: str,
    created_at: str,
    items: list[tuple[int, str]],
    match_pattern: list[str],
    with_error: bool,
    reporter_id: int,
    reviewer_id: int,
) -> None:
    cur = conn.cursor()
    cur.execute(
        """
        INSERT INTO review_sessions (id, prescription_id, created_by_pharmacist_id, status, llm_model_name, notes, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ChatGLM3', ?, ?, ?)
        """,
        (
            session_id,
            prescription_id,
            creator_id,
            status,
            SEED_TAG + " 模拟复核流水",
            created_at,
            created_at,
        ),
    )
    step_ids: list[int] = []
    for idx, ((item_id, herb), mst) in enumerate(zip(items, match_pattern), start=1):
        if mst == "correct":
            rec, conf = herb, 0.86
        elif mst == "incorrect":
            rec, conf = herb + "（误识）", 0.41
        elif mst == "needs_review":
            rec, conf = herb + "（待确认）", 0.55
        else:
            rec, conf = None, None
        cur.execute(
            """
            INSERT INTO session_steps (
                session_id, step_index, prescription_item_id, expected_herb_name,
                image_uri, llm_recognized_name, llm_confidence, llm_raw_response, match_status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                session_id,
                idx,
                item_id,
                herb,
                f"demo://image/{session_id}/{idx}.jpg",
                rec,
                conf,
                '{"demo":true,"model":"ChatGLM3"}',
                mst,
                created_at,
                created_at,
            ),
        )
        step_ids.append(int(cur.lastrowid))

    if with_error and step_ids:
        bad_step_id = step_ids[-1]
        cur.execute(
            """
            INSERT INTO error_reports (session_id, step_id, reported_by_pharmacist_id, description, status, created_at)
            VALUES (?, ?, ?, ?, 'resolved', ?)
            """,
            (
                session_id,
                bad_step_id,
                reporter_id,
                "模拟：识别与实物不符，已提交复核",
                created_at,
            ),
        )
        er_id = int(cur.lastrowid)
        cur.execute(
            """
            INSERT INTO error_report_reviews (error_report_id, reviewer_pharmacist_id, decision, agreed_herb_name, comment, created_at)
            VALUES (?, ?, 'adjust_recognition', ?, ?, ?)
            """,
            (
                er_id,
                reviewer_id,
                items[-1][1],
                "模拟：采纳处方应付药名",
                created_at,
            ),
        )
    conn.commit()


def seed_demo(conn: sqlite3.Connection) -> list[str]:
    director_id, other_id, _, _ = pick_pharmacists(conn)
    rx_list = pick_prescriptions(conn, limit=6)
    session_ids: list[str] = []

    base = datetime.now().replace(microsecond=0)

    scenarios = [
        {
            "rx": rx_list[0][0],
            "creator": other_id,
            "status": "completed",
            "offset_hours": -26,
            "pattern": ["correct", "correct", "incorrect", "correct"],
            "with_error": True,
            "reporter": other_id,
            "reviewer": director_id,
        },
        {
            "rx": rx_list[1][0],
            "creator": director_id,
            "status": "completed",
            "offset_hours": -18,
            "pattern": ["correct", "needs_review", "correct", "correct"],
            "with_error": False,
            "reporter": other_id,
            "reviewer": director_id,
        },
        {
            "rx": rx_list[2 % len(rx_list)][0],
            "creator": other_id,
            "status": "in_progress",
            "offset_hours": -3,
            "pattern": ["correct", "pending", "pending", "pending"],
            "with_error": False,
            "reporter": other_id,
            "reviewer": director_id,
        },
        {
            "rx": rx_list[3 % len(rx_list)][0],
            "creator": other_id,
            "status": "completed",
            "offset_hours": -50,
            "pattern": ["correct", "correct", "correct", "incorrect"],
            "with_error": True,
            "reporter": director_id,
            "reviewer": other_id,
        },
    ]

    for sc in scenarios:
        sid = str(uuid.uuid4())
        session_ids.append(sid)
        created = (base + timedelta(hours=sc["offset_hours"])).isoformat(sep=" ")
        items = fetch_items(conn, int(sc["rx"]), max_n=len(sc["pattern"]))
        insert_session_with_flow(
            conn,
            session_id=sid,
            prescription_id=int(sc["rx"]),
            creator_id=int(sc["creator"]),
            status=str(sc["status"]),
            created_at=created,
            items=items,
            match_pattern=list(sc["pattern"]),
            with_error=bool(sc["with_error"]),
            reporter_id=int(sc["reporter"]),
            reviewer_id=int(sc["reviewer"]),
        )
    return session_ids


def export_android_kotlin(conn: sqlite3.Connection, out_path: Path, session_ids: list[str]) -> None:
    cur = conn.cursor()
    placeholders = ",".join("?" * len(session_ids))
    cur.execute(
        f"""
        SELECT session_id, session_started_at, session_status, prescription_no, patient_name, diagnosis,
               created_by_name, created_by_employee_id, step_total, step_correct, step_incorrect, step_needs_review, error_reports_filed
        FROM director_work_overview
        WHERE session_id IN ({placeholders})
        ORDER BY session_started_at DESC
        """,
        session_ids,
    )
    overview_rows = cur.fetchall()

    cur.execute(
        f"""
        SELECT error_report_id, reported_at, report_status, step_index, expected_herb_name, llm_recognized_name,
               reported_by_name, prescription_no, review_id, reviewed_at, reviewer_name, decision, agreed_herb_name, review_comment
        FROM director_error_resolution_timeline
        WHERE error_report_id IN (
            SELECT id FROM error_reports WHERE session_id IN ({placeholders})
        )
        ORDER BY reported_at DESC
        LIMIT 40
        """,
        session_ids,
    )
    timeline_rows = cur.fetchall()

    cur.execute(
        f"""
        SELECT
            ss.session_id,
            ss.step_index,
            ss.expected_herb_name,
            ss.llm_recognized_name,
            ss.match_status,
            rr.decision,
            rr.agreed_herb_name,
            rev.full_name,
            rr.comment,
            CASE WHEN er.id IS NOT NULL THEN 1 ELSE 0 END AS has_error_report
        FROM session_steps ss
        LEFT JOIN error_reports er ON er.step_id = ss.id
        LEFT JOIN error_report_reviews rr ON rr.error_report_id = er.id
        LEFT JOIN pharmacists rev ON rev.id = rr.reviewer_pharmacist_id
        WHERE ss.session_id IN ({placeholders})
        ORDER BY ss.session_id, ss.step_index
        """,
        session_ids,
    )
    step_rows = cur.fetchall()
    steps_by_session: dict[str, list] = defaultdict(list)
    for r in step_rows:
        steps_by_session[str(r[0])].append(r)

    out_path.parent.mkdir(parents=True, exist_ok=True)

    def row_overview(r):
        return (
            f'DirectorWorkOverviewRow(\n'
            f'                sessionId = {_kotlin_escape(r[0])},\n'
            f'                sessionStartedAt = {_kotlin_escape(r[1])},\n'
            f'                sessionStatus = {_kotlin_escape(r[2])},\n'
            f'                prescriptionNo = {_kotlin_escape(r[3])},\n'
            f'                patientName = {_kotlin_str_or_null(r[4])},\n'
            f'                diagnosis = {_kotlin_str_or_null(r[5])},\n'
            f'                createdByName = {_kotlin_escape(r[6])},\n'
            f'                createdByEmployeeId = {_kotlin_escape(r[7])},\n'
            f'                stepTotal = {int(r[8])},\n'
            f'                stepCorrect = {int(r[9])},\n'
            f'                stepIncorrect = {int(r[10])},\n'
            f'                stepNeedsReview = {int(r[11])},\n'
            f'                errorReportsFiled = {int(r[12])},\n'
            f'            )'
        )

    def row_timeline(r):
        rid = r[8]
        review_id = "null" if rid is None else str(int(rid))
        return (
            f'DirectorErrorTimelineRow(\n'
            f'                errorReportId = {int(r[0])},\n'
            f'                reportedAt = {_kotlin_escape(r[1])},\n'
            f'                reportStatus = {_kotlin_escape(r[2])},\n'
            f'                stepIndex = {int(r[3])},\n'
            f'                expectedHerbName = {_kotlin_escape(r[4])},\n'
            f'                llmRecognizedName = {_kotlin_str_or_null(r[5])},\n'
            f'                reportedByName = {_kotlin_escape(r[6])},\n'
            f'                prescriptionNo = {_kotlin_escape(r[7])},\n'
            f'                reviewId = {review_id},\n'
            f'                reviewedAt = {_kotlin_str_or_null(r[9])},\n'
            f'                reviewerName = {_kotlin_str_or_null(r[10])},\n'
            f'                decision = {_kotlin_str_or_null(r[11])},\n'
            f'                agreedHerbName = {_kotlin_str_or_null(r[12])},\n'
            f'                reviewComment = {_kotlin_str_or_null(r[13])},\n'
            f'            )'
        )

    def row_step(r):
        has_er = bool(int(r[9])) if r[9] is not None else False
        return (
            f'DirectorSessionStepRow(\n'
            f'                sessionId = {_kotlin_escape(r[0])},\n'
            f'                stepIndex = {int(r[1])},\n'
            f'                herbName = {_kotlin_escape(r[2])},\n'
            f'                llmRecognizedName = {_kotlin_str_or_null(r[3])},\n'
            f'                matchStatus = {_kotlin_escape(r[4])},\n'
            f'                reviewDecision = {_kotlin_str_or_null(r[5])},\n'
            f'                reviewAgreedHerbName = {_kotlin_str_or_null(r[6])},\n'
            f'                reviewerName = {_kotlin_str_or_null(r[7])},\n'
            f'                reviewComment = {_kotlin_str_or_null(r[8])},\n'
            f'                hasErrorReport = {str(has_er).lower()},\n'
            f'            )'
        )

    kt = """package com.zhipu.herbreview.data

/**
 * 由 `herb_review_system/database/seed_director_demo.py` 根据本地 SQLite 视图导出。
 * 用于科主任「工作分析」界面与 Compose Preview；上线后改为接口/Room。
 */
data class DirectorWorkOverviewRow(
    val sessionId: String,
    val sessionStartedAt: String,
    val sessionStatus: String,
    val prescriptionNo: String,
    val patientName: String?,
    val diagnosis: String?,
    val createdByName: String,
    val createdByEmployeeId: String,
    val stepTotal: Int,
    val stepCorrect: Int,
    val stepIncorrect: Int,
    val stepNeedsReview: Int,
    val errorReportsFiled: Int,
)

data class DirectorErrorTimelineRow(
    val errorReportId: Int,
    val reportedAt: String,
    val reportStatus: String,
    val stepIndex: Int,
    val expectedHerbName: String,
    val llmRecognizedName: String?,
    val reportedByName: String,
    val prescriptionNo: String,
    val reviewId: Int?,
    val reviewedAt: String?,
    val reviewerName: String?,
    val decision: String?,
    val agreedHerbName: String?,
    val reviewComment: String?,
)

data class DirectorSessionStepRow(
    val sessionId: String,
    val stepIndex: Int,
    val herbName: String,
    val llmRecognizedName: String?,
    val matchStatus: String,
    val reviewDecision: String?,
    val reviewAgreedHerbName: String?,
    val reviewerName: String?,
    val reviewComment: String?,
    val hasErrorReport: Boolean,
)

object DirectorAnalyticsDemoData {
    val workOverview: List<DirectorWorkOverviewRow> = listOf(
"""
    kt += ",\n".join(row_overview(r) for r in overview_rows)
    kt += """
    )

    val errorTimeline: List<DirectorErrorTimelineRow> = listOf(
"""
    kt += ",\n".join(row_timeline(r) for r in timeline_rows)
    kt += """
    )

    val stepsBySessionId: Map<String, List<DirectorSessionStepRow>> = mapOf(
"""
    map_entries: list[str] = []
    for sid in session_ids:
        rows = steps_by_session.get(str(sid), [])
        inner = ",\n".join(row_step(r) for r in rows)
        map_entries.append(
            f'        {_kotlin_escape(str(sid))} to listOf(\n{inner}\n        )'
        )
    kt += ",\n".join(map_entries)
    kt += """
    )

    fun stepsFor(sessionId: String): List<DirectorSessionStepRow> =
        stepsBySessionId[sessionId] ?: emptyList()
}
"""
    out_path.write_text(kt, encoding="utf-8")


def export_review_flow_demo(conn: sqlite3.Connection, out_path: Path) -> None:
    """导出「复核」Tab 预设处方 + 与种子会话一致的识别流水（优先处方号 365624）。"""
    cur = conn.cursor()
    preferred_no = "365624"
    cur.execute("SELECT id FROM prescriptions WHERE prescription_no = ? LIMIT 1", (preferred_no,))
    row = cur.fetchone()
    if not row:
        cur.execute(
            """
            SELECT p.id
            FROM prescriptions p
            JOIN prescription_items i ON i.prescription_id = p.id
            GROUP BY p.id
            HAVING COUNT(*) >= 5
            ORDER BY p.id
            LIMIT 1
            """
        )
        row = cur.fetchone()
    if not row:
        raise RuntimeError("未找到可用处方行（prescriptions / prescription_items）")
    rx_id = int(row[0])

    cur.execute(
        """
        SELECT prescription_no, diagnosis, patient_gender, patient_age, prescribed_at
        FROM prescriptions
        WHERE id = ?
        """,
        (rx_id,),
    )
    head = cur.fetchone()
    cur.execute(
        """
        SELECT line_no, herb_name, dosage, usage_method
        FROM prescription_items
        WHERE prescription_id = ?
        ORDER BY line_no
        """,
        (rx_id,),
    )
    lines = cur.fetchall()

    cur.execute(
        """
        SELECT rs.id
        FROM review_sessions rs
        WHERE rs.prescription_id = ? AND rs.notes LIKE ?
        ORDER BY rs.created_at DESC
        LIMIT 1
        """,
        (rx_id, SEED_TAG + "%"),
    )
    srow = cur.fetchone()
    flow_rows: list = []
    if srow:
        sid = str(srow[0])
        cur.execute(
            """
            SELECT ss.step_index, ss.expected_herb_name, ss.llm_recognized_name, ss.match_status,
                   rr.decision, rr.agreed_herb_name, rev.full_name, rr.comment,
                   CASE WHEN er.id IS NOT NULL THEN 1 ELSE 0 END AS has_error_report
            FROM session_steps ss
            LEFT JOIN error_reports er ON er.step_id = ss.id
            LEFT JOIN error_report_reviews rr ON rr.error_report_id = er.id
            LEFT JOIN pharmacists rev ON rev.id = rr.reviewer_pharmacist_id
            WHERE ss.session_id = ?
            ORDER BY ss.step_index
            """,
            (sid,),
        )
        flow_rows = cur.fetchall()

    if flow_rows:
        max_step = max(int(r[0]) for r in flow_rows)
        lines = [ln for ln in lines if int(ln[0]) <= max_step]

    if not flow_rows:
        flow_rows = []
        for ln in lines[:8]:
            flow_rows.append(
                (
                    int(ln[0]),
                    str(ln[1]),
                    str(ln[1]),
                    "correct",
                    None,
                    None,
                    None,
                    None,
                    0,
                )
            )

    def row_line(ln):
        return (
            f'ReviewPrescriptionLineDemo(\n'
            f'                lineNo = {int(ln[0])},\n'
            f'                herbName = {_kotlin_escape(ln[1])},\n'
            f'                dosage = {_kotlin_str_or_null(ln[2])},\n'
            f'                usageMethod = {_kotlin_str_or_null(ln[3])},\n'
            f'            )'
        )

    def row_flow(r):
        has_er = bool(int(r[8])) if r[8] is not None else False
        return (
            f'ReviewFlowStepDemo(\n'
            f'                stepIndex = {int(r[0])},\n'
            f'                herbName = {_kotlin_escape(r[1])},\n'
            f'                llmRecognizedName = {_kotlin_str_or_null(r[2])},\n'
            f'                matchStatus = {_kotlin_escape(r[3])},\n'
            f'                reviewDecision = {_kotlin_str_or_null(r[4])},\n'
            f'                reviewAgreedHerbName = {_kotlin_str_or_null(r[5])},\n'
            f'                reviewerName = {_kotlin_str_or_null(r[6])},\n'
            f'                reviewComment = {_kotlin_str_or_null(r[7])},\n'
            f'                hasErrorReport = {str(has_er).lower()},\n'
            f'            )'
        )

    lines_joined = ",\n".join(row_line(ln) for ln in lines)
    flow_joined = ",\n".join(row_flow(r) for r in flow_rows)

    tag_literal = _kotlin_escape(SEED_TAG)
    rx_no_literal = _kotlin_escape(head[0])
    diag_literal = _kotlin_str_or_null(head[1])
    gender_literal = _kotlin_str_or_null(head[2])
    age_literal = _kotlin_str_or_null(head[3])
    date_literal = _kotlin_str_or_null(head[4])

    kt = (
        """package com.zhipu.herbreview.data

/**
 * 由 `herb_review_system/database/seed_director_demo.py` 从 prescriptions / prescription_items
 * 及同处方种子复核会话导出，用于「复核」Tab 预设案例。重新跑种子脚本后会更新。
 */
data class ReviewPrescriptionLineDemo(
    val lineNo: Int,
    val herbName: String,
    val dosage: String?,
    val usageMethod: String?,
)

data class ReviewPresetPrescriptionDemo(
    val prescriptionNo: String,
    val diagnosis: String?,
    val patientGender: String?,
    val patientAge: String?,
    val prescribedAt: String?,
    val lines: List<ReviewPrescriptionLineDemo>,
)

data class ReviewFlowStepDemo(
    val stepIndex: Int,
    val herbName: String,
    val llmRecognizedName: String?,
    val matchStatus: String,
    val reviewDecision: String?,
    val reviewAgreedHerbName: String?,
    val reviewerName: String?,
    val reviewComment: String?,
    val hasErrorReport: Boolean,
)

object ReviewFlowDemoData {
    /** 与数据库种子脚本 SEED_TAG 对齐，便于排查数据来源 */
    const val DEMO_SOURCE_TAG: String = """
        + tag_literal
        + """

    val presetPrescription: ReviewPresetPrescriptionDemo = ReviewPresetPrescriptionDemo(
        prescriptionNo = """
        + rx_no_literal
        + """,
        diagnosis = """
        + diag_literal
        + """,
        patientGender = """
        + gender_literal
        + """,
        patientAge = """
        + age_literal
        + """,
        prescribedAt = """
        + date_literal
        + """,
        lines = listOf(
"""
        + lines_joined
        + """
        ),
    )

    /** 与同处方种子会话 session_steps 一致的模拟识别流水（若未找到会话则为全「一致」占位） */
    val simulatedRecognitionFlow: List<ReviewFlowStepDemo> = listOf(
"""
        + flow_joined
        + """
    )
}
"""
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(kt, encoding="utf-8")


def main(argv: list[str]) -> int:
    default_db = _repo_root() / "herb_review_system" / "data" / "herb_review.db"
    default_kt = (
        _repo_root()
        / "herb_review_android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "zhipu"
        / "herbreview"
        / "data"
        / "DirectorAnalyticsDemoData.kt"
    )
    default_review_kt = (
        _repo_root()
        / "herb_review_android"
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "zhipu"
        / "herbreview"
        / "data"
        / "ReviewFlowDemoData.kt"
    )

    ap = argparse.ArgumentParser(description="写入复核模拟流水并导出 Android 演示数据")
    ap.add_argument("--db", type=Path, default=default_db)
    ap.add_argument(
        "--emit-android",
        type=Path,
        default=default_kt,
        help="导出 Kotlin 数据文件路径",
    )
    ap.add_argument("--no-seed", action="store_true", help="仅导出，不写入数据库")
    args = ap.parse_args(argv)

    db_path: Path = args.db
    if not db_path.exists():
        print(f"数据库不存在: {db_path}\n请先运行 import_from_excel.py", file=sys.stderr)
        return 1

    conn = sqlite3.connect(db_path)
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        recreate_director_views(conn)
        if not args.no_seed:
            removed = clear_seed_sessions(conn)
            print(f"已清除旧种子会话: {removed} 条")
            session_ids = seed_demo(conn)
            print(f"已写入新种子会话: {len(session_ids)} 条")
        else:
            cur = conn.cursor()
            cur.execute(
                f"SELECT id FROM review_sessions WHERE notes LIKE ? ORDER BY created_at DESC LIMIT 20",
                (SEED_TAG + "%",),
            )
            session_ids = [str(r[0]) for r in cur.fetchall()]
            if not session_ids:
                print("未找到种子会话，去掉 --no-seed 先写入。", file=sys.stderr)
                return 1

        export_android_kotlin(conn, args.emit_android, session_ids)
        print(f"已导出: {args.emit_android}")
        export_review_flow_demo(conn, default_review_kt)
        print(f"已导出: {default_review_kt}")
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
