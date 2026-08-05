# 12 AI Dev OS 安装完成后的验证流程

本章提供安装完成后从环境到项目功能的分层验证流程，
并给出最终验收清单。

> 前提：已完成 `10-deploy.md` 与 `11-service-start.md`，服务已启动。

---

## 1. 环境验证

```bash
# WSL2
wsl -l -v

# Docker
docker version
docker ps

# Java
java -version

# Maven
mvn -version

# Node / npm
node -v
npm -v
```

预期：

| 组件 | 预期 |
| --- | --- |
| WSL2 | 发行版 `VERSION` 为 2 |
| Docker | Client 与 Server 均正常，`docker ps` 无错误 |
| Java | 21.x |
| Maven | 3.9.x |
| Node / npm | Node 24.x / npm 11.x |

---

## 2. 数据库验证

```bash
# 连接
PGPASSWORD='<强密码>' psql -h localhost -p 5432 -U ai_dev_os -d ai_dev_os \
  -c "SELECT 1;"

# 迁移状态
PGPASSWORD='<强密码>' psql -h localhost -U ai_dev_os -d ai_dev_os \
  -c "SELECT version, description, success FROM schema_migrations ORDER BY version;"
```

预期：

- 连接返回 `1`
- `schema_migrations` 包含 V1～V7
- 各版本 `success` 均为 `true`

> 迁移文件：V1～V7（repository、audit、plan freeze、outbox、
> job 控制、coordinator、relay 控制）。

---

## 3. 后端验证

```bash
# 存活探针
curl -s http://127.0.0.1:18080/api/health

# 就绪探针
curl -s http://127.0.0.1:18080/api/health/readiness

# 端口监听
ss -ltnp | grep 18080
```

预期：

| 检查 | 预期 |
| --- | --- |
| `/api/health` | `200`，`{"status":"UP"}` |
| `/api/health/readiness` | 就绪 `200 READY`；未就绪 `503 NOT_READY` |
| 端口 | 后端进程监听 `18080` |

> readiness 未就绪时优先检查 PostgreSQL 连接与迁移状态。

---

## 4. 前端验证

```bash
# 页面访问
curl -s -I http://127.0.0.1:15174
ss -ltnp | grep 15174
```

预期：

- 页面返回 `200`
- Vite 进程监听 `15174`

API 代理验证：

- 浏览器访问 `http://127.0.0.1:15174`
- 页面发起的 `/api` 请求经 Vite 代理到后端 `18080`
- 后端日志出现对应请求记录

---

## 5. AI 工具链验证

### 5.1 Codex 启动

```bash
codex --version
cd ~/workspace/ai-dev-os && codex
```

预期：启动正常，能交互对话。

### 5.2 DeepSeek profile 切换

```bash
codex --profile deepseek
```

交互内执行：

```text
/model
```

预期：显示 DeepSeek 模型（如 `deepseek-chat`），详见 `07-deepseek.md`。

### 5.3 OpenClaw Gateway 状态

```bash
# Gateway 进程
ps aux | grep -E "openclaw.*gateway"

# 端口监听
ss -ltnp | grep 18789
```

预期：Gateway 监听 `ws://127.0.0.1:18789`。

### 5.4 MCP 工具检查

- 确认 `tools.mcp.enabled` 已按需开启
- 在 Codex 中检查 filesystem 等 server 为 connected
- 对应工具可用（详见 `09-mcp.md`）

---

## 6. 项目功能验证

### 6.1 Agent 流程

验证端到端链路：

```text
User Request → Hermes Plan → Plan Approval → PlanScheduler
→ Job → Agent → Tool/MCP → Execution → Audit Event → Timeline
```

### 6.2 Orchestrator

生产入口 API：

```bash
# 提交需求（示例）
curl -s -X POST http://127.0.0.1:18080/api/planning \
  -H "Content-Type: application/json" \
  -d '{"request": "分析仓库结构"}'

# 查询计划运行
curl -s http://127.0.0.1:18080/api/plan-runs
```

预期：请求被受理，返回计划/运行状态。

### 6.3 Job 执行

- 计划审批后生成 Job
- Job 由 Worker 领取执行，lease 机制保证不双执行
- 执行结果写入持久化存储（PostgreSQL 模式）

### 6.4 Audit / Timeline

- 关键事件（Plan、Step、Tool/MCP、Execution 等）生成审计事件
- 通过 Timeline API / Audit Console 查询
- 支持过滤、分页与计数

> 详细接口以 `docs/architecture` 与运行日志为准。

---

## 7. 测试验证

### 7.1 后端测试

```bash
cd services/orchestrator
./mvnw test
```

预期：

- 全量回归通过（当前基线 413 项，0 failure、0 error、1 skipped）
- Testcontainers PostgreSQL 测试依赖 Docker 可用

### 7.2 前端构建

```bash
cd services/orchestrator/frontend
npm run build
```

预期：

- TypeScript 检查通过（`vue-tsc --noEmit`）
- Vite 构建成功，产物生成到 `dist/`
- 存在已知 chunk size 警告，不影响构建成功

---

## 8. 验收清单

| 序号 | 验证项 | 命令 / 检查 | 通过标准 |
| --- | --- | --- | --- |
| 1 | WSL2 | `wsl -l -v` | 发行版版本为 2 |
| 2 | Docker | `docker ps` | 无错误 |
| 3 | Java 21 | `java -version` | 21.x |
| 4 | Maven | `mvn -version` | 3.9.x |
| 5 | Node/npm | `node -v && npm -v` | 24.x / 11.x |
| 6 | PostgreSQL | `psql ... SELECT 1` | 返回 1 |
| 7 | 迁移 V1～V7 | `schema_migrations` | 全部 success |
| 8 | 后端存活 | `/api/health` | 200 UP |
| 9 | 后端就绪 | `/api/health/readiness` | 200 READY |
| 10 | 前端页面 | `curl -I :15174` | 200 |
| 11 | Codex | `codex --version` | 正常输出版本 |
| 12 | DeepSeek | `/model` | 显示 DeepSeek 模型 |
| 13 | OpenClaw Gateway | `ss -ltnp \| grep 18789` | 监听中 |
| 14 | MCP 工具 | 工具列表 | filesystem 等 connected |
| 15 | 后端测试 | `./mvnw test` | 0 failure / 0 error |
| 16 | 前端构建 | `npm run build` | 构建成功 |

全部通过即视为 AI Dev OS v1.0 搭建验收完成。
