# 中草药智能复核 API（本地 / 预留上线）

## 功能概览

- **版本前缀**：`/api/v1`（便于网关与灰度）
- **数据库**：默认 **SQLite**（`../data/herb_review.db`）；设置 `DATABASE_URL` 即可切换 **PostgreSQL**（需安装 `psycopg[binary]` 等驱动后自行调整 URL 前缀）
- **认证**：`POST /api/v1/auth/login` 签发 JWT；开发环境可配合 `X-Dev-Pharmacist-Id`（见 `.env.example`）
- **业务**：处方检索与详情（含**全量**药材行）、复核会话创建（自动为每味药生成 `session_steps` 占位）、步骤 PATCH、科主任汇总只读接口
- **分析接口**：`director_work_overview` 与 `director_error_resolution_timeline` 都已开放只读 API

## 快速启动

```bash
cd herb_review_system/api
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

浏览器打开：`http://127.0.0.1:8000/docs`（Swagger UI）。可在 Swagger 中调用 **`GET /api/v1/diagnostics/integration`**，返回数据库与 `director_work_overview` 视图是否可用的分步结果（便于对照 App 上的自检面板）。

确保已存在数据库文件（可先运行 `database/import_from_excel.py`）。

可选：若要用 `fastapi.testclient` 做本地冒烟测试，请额外安装 `httpx`（`pip install httpx`）。

## 上线预留

- 设置环境变量 `DATABASE_URL` 指向 PostgreSQL
- 设置 `JWT_SECRET`、`CORS_ORIGINS`（逗号分隔具体域名）
- 将 `DEV_ALLOW_HEADER_AUTH=0`
- 可选：在 `API_ROOT_PATH` 填入反向代理子路径（如 `/herb-api`），与 `uvicorn` / Nginx 对齐

## Docker（可选）

```bash
docker build -t herb-review-api ./herb_review_system/api
docker run -p 8000:8000 -e DATABASE_URL=... -e JWT_SECRET=... herb-review-api
```
