# 中草药智能复核 APP — 后端与数据库路线图

本文档与当前仓库状态对齐：**Android 客户端（Kotlin/Compose）** + **本地 SQLite 原型** + **Excel 导入脚本**；后续在不大改领域模型的前提下，演进为**服务端 API + 生产级数据库**。

---

## 一、当前基线（你已完成的部分）

| 层级 | 内容 |
|------|------|
| 逻辑库 | `herb_review_system/database/schema.sql`：药师、处方、药材目录、复核会话、步骤、报错、复核结论、科主任视图 |
| 数据入口 | `import_from_excel.py`：三表 Excel → SQLite |
| 演示种子 | `seed_director_demo.py`：复核流水 + 导出 `DirectorAnalyticsDemoData.kt` / `ReviewFlowDemoData.kt` |
| 客户端 | 复核/科主任界面仍走**静态导出**；上线前需改为 **HTTP + 鉴权** |

约定：领域表名与字段尽量保持稳定，后续只做**增量迁移**与**只读视图扩展**。

---

## 二、目标架构（建议）

```
Android APP
    │  HTTPS (JSON) + 可选 WSS/SSE (ChatGLM3 流式)
    ▼
API 网关（鉴权、限流、审计）
    │
    ├── 业务服务：处方、复核会话、步骤、报错、推送、科主任报表
    │
    ├── 推理适配层：ChatGLM3（本机 vLLM / HF / 自建网关，与 APP 解耦）
    │
    └── 关系型 DB（主存） + 对象存储（处方/复核图片）
```

**技术选型建议（与现有 Python 生态衔接）**

- **API**：FastAPI + Uvicorn（仓库已有 fastapi 相关 demo，团队熟悉曲线低）
- **生产 DB**：PostgreSQL 15+（JSONB、行级安全、备份成熟）；开发环境可仍用 SQLite 或 Docker PG
- **迁移**：Alembic 或 Flyway（二选一，全团队统一）
- **对象存储**：MinIO（私有化）或云厂商 OSS（图片、导出报表）
- **缓存/队列**（可选第二阶段）：Redis + Celery/RQ（全员推送、异步识别任务）
- **鉴权**：短期 JWT + Refresh；院内可对接 LDAP/企业微信（后续）

---

## 三、数据库演进规划

### 3.1 与「全处方药材」对齐（你提到的后续项）

当前种子为演示只写了部分 `session_steps`。**真实接入后**应满足：

- 每个 `review_sessions` 对应一张处方时，`session_steps` 行数 **=** `prescription_items` 行数（或明确子集策略，如只复核毒麻品种，用策略表记录）
- 约束建议：`UNIQUE(session_id, step_index)` 已具备；增加 `CHECK(step_index BETWEEN 1 AND (SELECT COUNT FROM prescription_items ...))` 在应用层校验即可（SQLite 跨表 CHECK 较弱）

### 3.2 建议新增/调整的表与字段（按优先级）

**P0（上线最小集）**

| 变更 | 说明 |
|------|------|
| `pharmacists.password_hash` | 替换明文；保留 `password_credential` 仅迁移期 |
| `review_sessions` 增加 `client_device_id`、`closed_at` | 审计与统计 |
| `session_steps` 增加 `image_object_key`、`thumbnail_object_key` | 对接对象存储 |
| `app_audit_log` | `who, when, action, entity_type, entity_id, payload_json` |

**P1（协作与通知）**

| 表 | 说明 |
|----|------|
| `notifications` | 报错广播、复核指派、已读状态 |
| `session_step_events` | 可选：逐步 append-only 事件流，便于重放与纠纷追溯 |

**P2（统计与导出）**

| 视图/物化视图 | 说明 |
|---------------|------|
| 科主任报表 | 现有 `director_work_overview` 逻辑迁移到 PG 视图或定时物化表 |
| `export_jobs` | 异步导出 Excel/PDF |

### 3.3 索引与性能（处方量大时）

- `prescription_items(prescription_id, line_no)` 已有；补充 `prescriptions(prescription_no)` 唯一索引（若未显式）
- `session_steps(session_id)`、`error_reports(status, created_at)`、`review_sessions(created_at)`

---

## 四、API 规划（资源导向）

### 4.1 认证

- `POST /auth/login` → `{ access_token, refresh_token, pharmacist }`
- `POST /auth/refresh`
- `GET /auth/me`

### 4.2 主数据（与 Excel 同源）

- `GET /prescriptions?query=&page=` 搜索处方号
- `GET /prescriptions/{id}` 抬头 + `items[]` **全量药材**
- `GET /herbs?query=` 药材目录检索（可选）

### 4.3 复核会话（核心）

- `POST /review-sessions` body: `{ prescription_id }` → 创建会话，**预生成 N 条 step 占位**（N = 处方明细行数）或懒创建第一步（产品二选一）
- `GET /review-sessions/{id}` 含 steps
- `PATCH /review-sessions/{id}/steps/{stepId}` 上传图、触发 LLM、写回 `llm_*`、`match_status`
- `POST /review-sessions/{id}/steps/{stepId}/report-error` → `error_reports`
- `POST /error-reports/{id}/reviews` → `error_report_reviews`，并更新 `error_reports.status`

### 4.4 ChatGLM3

- `POST /chat/completions` 或 `GET /chat/stream?...`（SSE）  
- 服务端组装 prompt：处方上下文 + 当前 step 应付药名 + 图像特征/多模态输入（取决于部署形态）

### 4.5 科主任

- `GET /analytics/sessions` 过滤日期、药师 → 对齐现有 overview
- `GET /analytics/error-timeline`

### 4.6 文件

- `POST /uploads/presigned` 或直传 `POST /uploads`（小文件原型）

---

## 五、安全与合规（中医院场景）

- **患者隐私**：日志与 API 默认脱敏；导出需角色权限
- **密码**：只存 Argon2id/bcrypt；登录失败退避
- **审计**：所有写操作记 `app_audit_log`；科主任导出单独记
- **免责文案**：由院方审定，接口返回 `legal_notice_version` 供 APP 展示

---

## 六、分阶段交付（建议迭代顺序）

| 阶段 | 目标 | 产出 |
|------|------|------|
| **0** | 冻结领域模型与 API 草案 | OpenAPI 草稿 + 本路线图 |
| **1** | 可登录 + 只读处方全量 | DB 迁移脚本 + `GET prescriptions` + Android 换接口读列表 |
| **2** | 复核会话 CRUD + 步骤写回 | `POST/PATCH review-sessions` + 图片占位 URL |
| **3** | 报错/复核闭环 + 推送占位 | `error_reports` API + 轮询或 FCM |
| **4** | LLM 接入 | 推理服务独立部署，API 仅转发与计费 |
| **5** | 科主任报表与导出 | 报表 API + CSV |

每阶段结束：**迁移脚本 + 集成测试 + Android 对接清单**。

---

## 七、与 Android 的对接清单（备忘）

- 将 `ReviewFlowDemoData` / `DirectorAnalyticsDemoData` 替换为 **Repository + Retrofit/Ktor**
- 统一错误模型与 Token 刷新拦截器
- 复核 Step3 表格数据改来自 `GET /review-sessions/{id}` 的 `steps[]`
- 图片：Camera → 压缩 → 上传 → 将返回 URL 写入 step

---

## 八、下一步建议（立即可以开工的第一项）

1. 在 `herb_review_system/` 下新建 **`api/`** 子项目：`pyproject.toml` 或 `requirements-api.txt`，最小 FastAPI 工程，`/health`、`/prescriptions` 只读 SQLite 或 PG。  
2. 用 **OpenAPI** 生成 Android 的接口常量（或手写 DTO 与路径一致）。  
3. 把「全量药材」读路径走通：`GET /prescriptions/{id}/items` 返回与 `prescription_items` 一致的结构。

完成以上三项后，再切复核会话写入，即可自然支持「展示处方中**所有**药材」而不仅限于演示用的 4 步。

---

*文档版本：与仓库同步维护；重大架构变更时请更新「目标架构」与「分阶段交付」两节。*
