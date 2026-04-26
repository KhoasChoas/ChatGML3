-- 中草药智能复核 APP — 本地 SQLite 逻辑库
-- 说明：基础主数据来自 Excel；业务过程表支撑识别流程、报错与复核、科主任汇总。
-- 生产环境请迁移至服务端 DB，并对密码做哈希、对敏感字段加密。

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS app_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 1) 药师基本信息（源自 Excel「药师基本信息表」）
CREATE TABLE IF NOT EXISTS pharmacists (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    seq_no                  INTEGER,
    full_name               TEXT NOT NULL,
    employee_id             TEXT NOT NULL UNIQUE,
    gender                  TEXT,
    phone                   TEXT,
    title_rank              TEXT,   -- 职称
    job_title               TEXT,   -- 职务（用于判断药剂科主任）
    department              TEXT,   -- 科室
    password_credential     TEXT,   -- 原型阶段存导入值；上线改为 password_hash
    is_department_director  INTEGER NOT NULL DEFAULT 0 CHECK (is_department_director IN (0, 1)),
    can_submit_error_report INTEGER NOT NULL DEFAULT 1 CHECK (can_submit_error_report IN (0, 1)),
    imported_at             TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_pharmacists_dept ON pharmacists(department);
CREATE INDEX IF NOT EXISTS idx_pharmacists_director ON pharmacists(is_department_director);

-- 2) 中草药信息（源自 Excel「中草药信息表」）
CREATE TABLE IF NOT EXISTS herb_catalog (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    drug_code               TEXT NOT NULL UNIQUE,
    herb_name               TEXT NOT NULL,
    slice_traits            TEXT,   -- 饮片性状
    processing_method       TEXT,   -- 炮制方法
    nature_flavor_meridian  TEXT,   -- 性味归经
    functions_indications   TEXT,   -- 功能主治
    usage_dosage            TEXT,   -- 用法用量
    main_chemistry          TEXT,   -- 主要有效化学成分
    precautions             TEXT,   -- 注意事项
    imported_at             TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_herb_catalog_name ON herb_catalog(herb_name);

-- 3) 处方主表（源自 Excel「中草药处方表」分组后的抬头）
CREATE TABLE IF NOT EXISTS prescriptions (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    source_seq_no           INTEGER,          -- Excel 序号
    prescription_no         TEXT NOT NULL UNIQUE,
    patient_name            TEXT,
    patient_gender          TEXT,
    patient_age             TEXT,
    fee_type                TEXT,
    medical_record_no       TEXT,
    dept_bed                TEXT,
    address                 TEXT,
    phone                   TEXT,
    diagnosis               TEXT,
    prescribed_at           TEXT,             -- 开方日期（保留原始字符串）
    herb_kind_count         INTEGER,
    drug_fee                REAL,
    injection_fee           REAL,
    prescribing_doctor      TEXT,
    dispensing_pharmacist   TEXT,
    reviewing_doctor        TEXT,
    imported_at             TEXT NOT NULL DEFAULT (datetime('now'))
);

-- 处方明细（每味药一行）
CREATE TABLE IF NOT EXISTS prescription_items (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    prescription_id         INTEGER NOT NULL,
    line_no                 INTEGER NOT NULL,
    herb_name               TEXT NOT NULL,
    dosage                  TEXT,
    usage_method            TEXT,
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
    UNIQUE (prescription_id, line_no)
);

CREATE INDEX IF NOT EXISTS idx_rx_items_rx ON prescription_items(prescription_id);

-- 4) 一次「智能复核」会话：选定处方后，按顺序对多张照片识别
CREATE TABLE IF NOT EXISTS review_sessions (
    id                          TEXT PRIMARY KEY,  -- UUID
    prescription_id             INTEGER NOT NULL,
    created_by_pharmacist_id    INTEGER NOT NULL,
    status                      TEXT NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'in_progress', 'completed', 'cancelled')),
    llm_model_name              TEXT DEFAULT 'ChatGLM3',
    notes                       TEXT,
    created_at                  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at                  TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by_pharmacist_id) REFERENCES pharmacists(id)
);

CREATE INDEX IF NOT EXISTS idx_review_sessions_rx ON review_sessions(prescription_id);
CREATE INDEX IF NOT EXISTS idx_review_sessions_created_by ON review_sessions(created_by_pharmacist_id);

-- 每一步对应一味「应识别」药材 + 一张用户提交的图 + LLM 结论
CREATE TABLE IF NOT EXISTS session_steps (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id              TEXT NOT NULL,
    step_index              INTEGER NOT NULL,
    prescription_item_id    INTEGER,
    expected_herb_name      TEXT NOT NULL,
    image_uri               TEXT,             -- APP 本地路径或后续对象存储 URL
    llm_recognized_name     TEXT,
    llm_confidence          REAL,
    llm_raw_response        TEXT,
    match_status            TEXT NOT NULL DEFAULT 'pending'
        CHECK (match_status IN ('pending', 'correct', 'incorrect', 'needs_review')),
    reviewer_comment        TEXT,             -- 简要备注（可选）
    created_at              TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at              TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (prescription_item_id) REFERENCES prescription_items(id),
    UNIQUE (session_id, step_index)
);

CREATE INDEX IF NOT EXISTS idx_session_steps_session ON session_steps(session_id);

-- 5) 识别错误上报（提交后端后广播给全体药师——由服务端实现推送；库中记录状态）
CREATE TABLE IF NOT EXISTS error_reports (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id                  TEXT NOT NULL,
    step_id                     INTEGER NOT NULL,
    reported_by_pharmacist_id   INTEGER NOT NULL,
    description                 TEXT,
    status                      TEXT NOT NULL DEFAULT 'open'
        CHECK (status IN ('open', 'notified', 'resolved', 'withdrawn')),
    created_at                  TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (session_id) REFERENCES review_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (step_id) REFERENCES session_steps(id) ON DELETE CASCADE,
    FOREIGN KEY (reported_by_pharmacist_id) REFERENCES pharmacists(id)
);

CREATE INDEX IF NOT EXISTS idx_error_reports_status ON error_reports(status);

-- 任意药师（含提交者）对报错单的复核结论
CREATE TABLE IF NOT EXISTS error_report_reviews (
    id                          INTEGER PRIMARY KEY AUTOINCREMENT,
    error_report_id             INTEGER NOT NULL,
    reviewer_pharmacist_id      INTEGER NOT NULL,
    decision                    TEXT NOT NULL
        CHECK (decision IN ('confirm_error', 'reject_error', 'adjust_recognition')),
    agreed_herb_name            TEXT,   -- 复核后采纳的药材名（如 adjust 时）
    comment                     TEXT,
    created_at                  TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (error_report_id) REFERENCES error_reports(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_pharmacist_id) REFERENCES pharmacists(id)
);

CREATE INDEX IF NOT EXISTS idx_err_reviews_report ON error_report_reviews(error_report_id);

-- 科主任：工作记录整合视图（按会话聚合，含报错与复核摘要）
CREATE VIEW IF NOT EXISTS director_work_overview AS
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

CREATE VIEW IF NOT EXISTS director_error_resolution_timeline AS
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
