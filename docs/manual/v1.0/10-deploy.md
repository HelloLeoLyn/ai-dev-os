# 10 AI Dev OS 项目部署

本章完成 AI Dev OS v1.0 项目的获取、构建与部署配置。

> 前提：已完成 `04-toolchain.md`（Java/Maven/Node）与
> `05-postgresql.md`（PostgreSQL 可选，开发可先用 in-memory）。

---

## 1. 获取代码

```bash
mkdir -p ~/workspace
cd ~/workspace
git clone <AI-Dev-OS-仓库地址>
cd ai-dev-os
```

workspace 目录规划：

```text
/home/<用户>/workspace/ai-dev-os
```

- 项目放在 WSL 文件系统内，避免 `/mnt/c` 跨文件系统 IO 慢
- 克隆后确认分支（如 `main`）与版本基线

---

## 2. 项目结构说明

```text
ai-dev-os/
├── services/
│   └── orchestrator/          # 后端服务（Spring Boot）
│       ├── src/main/          # 后端源码与配置
│       ├── frontend/          # 前端（Vue 3 + Vite）
│       ├── Dockerfile         # 后端容器镜像
│       ├── pom.xml            # Maven 构建
│       └── scripts/           # 启动脚本（start-all / backend / frontend）
├── docs/                      # 文档（architecture / operation / manual）
├── configs/                   # Agent 与工作流配置
├── .mcp/                      # MCP 配置与权限
└── scripts/                   # 仓库根脚本目录（当前为空，占位）
```

说明：

- 后端：`services/orchestrator`，Java 21 + Spring Boot 4 + Maven
- 前端：`services/orchestrator/frontend`，Vue 3 + Vite
- 实际启动脚本位于 `services/orchestrator/scripts/`
  （`start-all.sh` / `start-backend.sh` / `start-frontend.sh`）
- 仓库根 `scripts/` 目前为空，属占位目录

---

## 3. 后端部署

### 3.1 Java / Maven 构建

```bash
cd services/orchestrator
./mvnw clean package -DskipTests
```

产物：

```text
target/orchestrator-0.0.1-SNAPSHOT.jar
```

运行：

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

### 3.2 配置文件

- `src/main/resources/application.properties`：主配置，全部支持环境变量覆盖
- `src/main/resources/application-local.properties`：本地 profile
  （OpenClaw gateway 与默认 token，仅开发用）

启动脚本使用：

```bash
./mvnw spring-boot:run --server.port=18080 --spring.profiles.active=local
```

### 3.3 环境变量

关键变量：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_DEV_OS_PERSISTENCE_TYPE` | `in-memory` | 持久化模式 |
| `AI_DEV_OS_POSTGRES_URL` | `jdbc:postgresql://localhost:5432/ai_dev_os` | 连接串 |
| `AI_DEV_OS_POSTGRES_USER` | `ai_dev_os` | 数据库用户 |
| `AI_DEV_OS_POSTGRES_PASSWORD` | 空 | 数据库密码 |
| `OPENCLAW_GATEWAY_URL` | `ws://127.0.0.1:18789` | OpenClaw Gateway |
| `OPENCLAW_GATEWAY_TOKEN` | 本地默认值 | 生产必须覆盖 |
| `CODEX_EXECUTABLE` | `codex` | Codex 可执行文件 |
| `CODEX_APPROVAL_POLICY` | `never` | 自动化审批策略 |

---

## 4. 前端部署

```bash
cd services/orchestrator/frontend
npm install
npm run build
```

产物：

```text
frontend/dist/
```

开发模式启动（由 `start-frontend.sh` 使用）：

```bash
npm run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

说明：

- Vite 开发服务器代理 API 到 `http://127.0.0.1:18080`
- 生产部署可托管 `dist/` 静态文件并反向代理 `/api` 到后端

---

## 5. PostgreSQL 连接配置

设置持久化环境变量：

```bash
export AI_DEV_OS_PERSISTENCE_TYPE=postgresql
export AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://localhost:5432/ai_dev_os
export AI_DEV_OS_POSTGRES_USER=ai_dev_os
export AI_DEV_OS_POSTGRES_PASSWORD='<强密码>'
```

- 应用启动时自动执行 V1～V7 迁移并记录到 `schema_migrations`
- 迁移完成前 readiness 返回 `503 NOT_READY`
- 详细建库与验证见 `05-postgresql.md`

---

## 6. Docker 相关

### 6.1 Dockerfile 说明

`services/orchestrator/Dockerfile` 为多阶段构建：

1. 构建阶段：`eclipse-temurin:21-jdk`，`mvnw clean package`
2. 运行阶段：`eclipse-temurin:21-jre`，`java -jar app.jar`

```bash
cd services/orchestrator
docker build -t ai-dev-os/orchestrator:0.0.1-SNAPSHOT .
```

注意：Dockerfile 中 `EXPOSE 8080`，而应用实际监听
`server.port=18080`，容器部署时需显式映射 `-p 18080:18080`
（或通过 `SERVER_PORT` 对齐）。

### 6.2 当前容器化状态说明

- 后端 Dockerfile 已就绪
- 仓库根 `docker/` 为空，无 docker-compose 编排
- PostgreSQL 容器化见 `03-docker.md` / `05-postgresql.md`
- orchestrator + PostgreSQL 的 compose 编排待落地

---

## 7. 生产模式配置

生产模式要点：

1. `AI_DEV_OS_PERSISTENCE_TYPE=postgresql`
2. 提供数据库密码，禁止默认空密码
3. 覆盖 `OPENCLAW_GATEWAY_TOKEN`，不使用本地默认值
4. 按需配置 `CODEX_APPROVAL_POLICY` 与工作区限制
5. 使用 readiness 探针接入流量

```bash
export AI_DEV_OS_PERSISTENCE_TYPE=postgresql
export AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://localhost:5432/ai_dev_os
export AI_DEV_OS_POSTGRES_USER=ai_dev_os
export AI_DEV_OS_POSTGRES_PASSWORD='<强密码>'
export OPENCLAW_GATEWAY_TOKEN='<强随机Token>'

java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

readiness 检查：

```bash
curl -s http://127.0.0.1:18080/api/health
curl -s http://127.0.0.1:18080/api/health/readiness
```

预期：

- `/api/health`：`200`，`{"status":"UP"}`
- `/api/health/readiness`：就绪 `200 READY`；未就绪 `503 NOT_READY`

---

## 8. 部署验证

按顺序验证：

```bash
# 1. 后端存活
curl -s http://127.0.0.1:18080/api/health

# 2. 后端就绪（含数据库迁移）
curl -s http://127.0.0.1:18080/api/health/readiness

# 3. 前端页面
curl -s -I http://127.0.0.1:15174

# 4. 数据库连接与迁移
PGPASSWORD='<强密码>' psql -h localhost -U ai_dev_os -d ai_dev_os \
  -c "SELECT version, success FROM schema_migrations ORDER BY version;"

# 5. 一键启动脚本（开发环境）
services/orchestrator/scripts/start-all.sh
```

预期结果：

- 后端 `UP` 且 readiness `READY`
- 前端页面返回 `200`
- `schema_migrations` 含 V1～V7 且 `success` 均为 `true`

> 完整运维与故障恢复见 `docs/operation/runbook.md`。

---

## 9. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 构建失败：Java 版本错误 | 默认 JDK 非 21 | 确认 `java -version` 为 21，检查 `JAVA_HOME` |
| Maven 依赖下载失败 | 网络问题 | 配置阿里云 mirror 后重试 |
| 前端 `npm install` 失败 | registry 不可达 | 配置 npmmirror registry |
| `node_modules` 缺失导致启动失败 | 未安装依赖 | 先 `cd frontend && npm install` |
| 端口占用（18080/15174） | 已有进程监听 | `ss -ltnp` 检查并释放端口 |
| readiness 一直 503 | 数据库未就绪或迁移失败 | 检查 PostgreSQL 连接与 `schema_migrations` |
| 连接数据库失败 | 密码或连接串错误 | 核对 `AI_DEV_OS_POSTGRES_*` 环境变量 |
| 容器端口访问不通 | EXPOSE 8080 与实际 18080 不一致 | 显式映射 `-p 18080:18080` |
| 生产使用了默认 OpenClaw token | 本地 profile 默认值未覆盖 | 设置强随机 `OPENCLAW_GATEWAY_TOKEN` |
| 前端构建 chunk 警告 | 生产构建体积较大 | 属已知治理项，不影响构建成功 |
