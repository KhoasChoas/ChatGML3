# 中草药智能复核 APP — 前端设计方案

面向：**药剂师日常复核** + **科主任工作分析**；大模型固定为 **ChatGLM3**（推理可在后续后端中对接本地或网关）。

本文档描述信息架构、关键界面、交互状态与与数据库表的对应关系。

**实现栈（已定）**：仅 **Android**，工程见仓库根目录 `herb_review_android/`（Kotlin + Jetpack Compose + Material 3）。iOS 不在当前范围。

---

## 1. 角色与权限

| 角色 | 识别与 LLM 对话 | 按处方顺序识图 | 查看结果表 | 提交识别错误 | 复核他人报错 | 科主任汇总 |
|------|----------------|----------------|------------|--------------|--------------|----------|
| 普通药师 | ✓ | ✓ | ✓ | 需 `can_submit_error_report=1`（默认 1） | ✓（含本人提交的工单） | ✗ |
| 药剂科主任 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓（`is_department_director=1`） |

- **登录**：用工号 + 密码（原型阶段与 Excel「密码」一致；上线改为服务端哈希校验）。
- **科主任入口**：登录后若 `is_department_director=1`，主导航增加 **「工作分析」**（或放在「我的」中的隐藏入口，由产品定）。

---

## 2. 信息架构（主导航）

建议底部 **4 Tab**（或 3 Tab + 更多）：

1. **工作台**：今日任务、继续未完成的复核会话、快捷搜索处方。
2. **复核**：选择处方 → 逐步拍照/选图 → LLM 识别 → 结果表；此处嵌入 **与 ChatGLM3 的对话**（解释、追问药材性状等）。
3. **报错台**：待办/已办「识别错误工单」列表（对应 `error_reports` + `error_report_reviews`）。
4. **我的**：账号信息、设置（服务端地址、模型参数占位）、**科主任可见「工作分析」**。

---

## 3. 核心用户流程

### 3.1 处方驱动的顺序识图（需求 1）

1. 用户在工作台或复核 Tab **搜索/扫码处方编号**（`prescriptions.prescription_no`）。
2. 进入 **「复核会话」**（`review_sessions`）：展示处方抬头与 **药材明细列表**（`prescription_items`，按 `line_no` 排序）。
3. **顺序约束**：默认从第 1 味药开始；用户也可在主任允许的策略下跳序（可选，首版可强制顺序）。
4. 每一步：
   - 展示当前 **应付药材**：名称、规格（用量/用法来自明细）。
   - 用户 **拍照或选图** → 本地生成 `image_uri`（占位）；提交后端后替换为 URL。
   - 前端调用 **LLM（ChatGLM3）**（经后续 API）：传入「处方上下文 + 当前应付药名 + 图像特征/标签（若先做纯文本则传用户简短描述）」。
   - 将返回写入 `session_steps`：`llm_recognized_name`、`llm_raw_response`、`match_status`（`correct` / `incorrect` / `needs_review`）。
5. 全部步骤完成后 `review_sessions.status = completed`。

**与 LLM 的交互方式**：在复核页提供 **可折叠对话区**，与识别区并存：用户可就「本味药性状、易混品、煎服注意」等追问，上下文自动带上当前处方与当前步骤药材（提示词由后端统一模板化）。

### 3.2 识别错误上报与全员复核（需求 2）

1. 当某步 `match_status = incorrect` 或用户人工判定错误时，若用户有报错权限，显示 **「提交错误」**。
2. 填写简要说明 → 插入 `error_reports`（`status=open`）；后续后端广播给全体药师后更新为 `notified`。
3. **报错台** 列表：所有药师可见；任一人打开工单，查看关联 `session_steps` 截图与 LLM 原文。
4. 复核人提交结论 → `error_report_reviews`：`decision` = `confirm_error` | `reject_error` | `adjust_recognition`，可选 `agreed_herb_name`。
5. 工单 `error_reports.status` 置为 `resolved`（规则：例如「至少一条 review」或「主任终审」——首版可采用「任一复核即 resolved」，产品可升级）。

**说明**：提交过错误的药师 **可以** 再次复核同一单（需求已明确），不在前端拦截，仅审计日志记录。

### 3.3 识别过程可视化（需求 3）

在会话内与同会话详情页展示 **结果表**（建议 `DataTable` / 可横向滚动卡片）：

| 列 | 说明 |
|----|------|
| 步骤序号 | `step_index` |
| 应付药材 | `expected_herb_name`（可链到 `herb_catalog` 展示性状摘要） |
| 用户图缩略图 | `image_uri` |
| LLM 识别结果 | `llm_recognized_name` + 置信度（若有） |
| 判定 | `match_status` → 展示为 正确 / 错误 / 待复核 |
| 报错状态 | 关联 `error_reports`：未报 / 待处理 / 已处理 |
| 复核结论 | 最新一条 `error_report_reviews`：结论 + 采纳药名 + 复核人 |

支持按步骤 **展开**：显示 `llm_raw_response` 全文、时间戳、操作人。

### 3.4 科主任 — 记录保留与整合（需求 4）

**入口**：仅 `is_department_director=1`。

**页面 A — 汇总看板**（读 `director_work_overview`）：

- 时间范围筛选、按药师筛选、按处方号搜索。
- 卡片指标：会话数、总步骤数、正确率、报错次数。
- 列表：每条 `session_id`、处方号、发起人、起止时间、会话状态。

**页面 B — 报错与复核时间线**（读 `director_error_resolution_timeline`）：

- 时间线展示：上报人、步骤、识别偏差、谁复核、结论。

**导出**（后续）：CSV / Excel，首版可不做，仅在设计中预留按钮位。

---

## 4. 界面清单（供 UI 出稿）

1. **登录页**
2. **工作台首页**（搜索处方、继续会话）
3. **处方详情页**（抬头 + 药材列表 +「开始复核」）
4. **复核会话页**（步骤指示器、相机/相册、LLM 对话抽屉、底部「下一步」）
5. **会话结果总览页**（结果表，需求 3）
6. **报错台列表 / 工单详情**（需求 2）
7. **科主任 — 工作分析**（需求 4）
8. **我的 / 设置**

---

## 5. 关键前端状态（建议）

- `auth`: 当前药师 `id`、`is_department_director`、`can_submit_error_report`
- `activeSession`: `review_sessions.id`、`prescription_id`、当前 `step_index`
- `steps[]`: 与 `session_steps` 同步的本地缓存，支持弱网离线草稿（可选）
- `llmChat`: 与 ChatGLM3 的对话消息列表（会话级或全局级由产品定）

---

## 6. 与本地数据库 / 后续 API 的边界

- **当前阶段**：可使用 **SQLite**（`herb_review_system/data/herb_review.db`）做原型读写；移动端可将只读主数据打包或经同步接口下发。
- **后续后端**：将 `INSERT/UPDATE` 会话、步骤、报错、复核 改为 REST / WebSocket；**推送全员报错** 由服务端通过 FCM/厂商推送或轮询列表实现。

---

## 7. ChatGLM3 在前端的呈现要点

- 流式输出组件（SSE）；**停止生成**按钮。
- 对 **图像识别**：若首版无多模态，可采用「药师选关键特征 + LLM 文本推理」过渡；与后端对齐后改为真正视觉模型或 ChatGLM3 多模态能力（取决于你方部署）。

---

## 8. 非功能需求提示

- 处方与患者信息：**脱敏展示**（姓名可部分掩码，电话掩码）。
- 审计：关键操作打点（谁、何时、对哪一步），与后端日志对齐。
- 合规文案：界面底部固定「辅助决策，最终以人工复核为准」等声明（文案由院方审定）。

---

## 9. 仓库内已实现的数据库工件

- 逻辑结构：`herb_review_system/database/schema.sql`
- Excel 导入：`herb_review_system/database/import_from_excel.py`（默认读取 `E:\Other\Work\Excel`）
- 导入产物：`herb_review_system/data/herb_review.db`

前端实现阶段可直接按本方案中的表名与视图名对接 SQL 或后续 ORM。
