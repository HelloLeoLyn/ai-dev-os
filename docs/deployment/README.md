# AI Dev OS 部署指南

本指南覆盖本地运行、Docker 部署、生产环境变量、数据库初始化与备份恢复。

## 1. 本地运行

### 前置条件

- JDK 21
- Maven 3.9+
- Node.js 22+（仅前端）
- Docker（可选，用于 PostgreSQL）

### 后端

```bash
cd services/orchestrator
mvn spring-boot:run
```

默认使用内存持久化（`aidevos.persistence.type=in-memory`），服务监听 `18080`。

### 前端

```bash
cd services/orchestrator/frontend
npm install
npm run dev
```

开发服务器默认代理 `/api` 到 `http://127.0.0.1:18080`。

## 2. Docker 部署

根目录 `docker-compose.yml` 会启动三个服务：

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| postgres | 5432（内部） | PostgreSQL 17，数据持久化在 `postgres-data` 卷 |
| orchestrator | 18080 | Spring Boot 服务，`prod` profile + PostgreSQL 持久化 |
| frontend | 8080 | nginx 托管 Vue 前端，`/api` 反向代理到 orchestrator |

```bash
docker compose up -d --build
docker compose ps
curl http://127.0.0.1:18080/actuator/health
curl http://127.0.0.1:8080
```

停止：

```bash
docker compose down          # 保留数据卷
docker compose down -v       # 同时删除数据库数据卷
```

## 3. 生产环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/ai_dev_os` | 数据库 JDBC URL |
| `POSTGRES_USER` | `ai_dev_os` | 数据库用户 |
| `POSTGRES_PASSWORD` | 无 | 数据库密码（生产必须设置） |
| `SPRING_PROFILES_ACTIVE` | `prod`（Docker 镜像内已设置） | 激活生产 profile |
| `AI_DEV_OS_WORKSPACE_ROOT` | `/workspace` | Agent Workspace 根目录 |
| `OPENCLAW_GATEWAY_URL` | `ws://host.docker.internal:18789` | OpenClaw 网关地址 |
| `OPENCLAW_GATEWAY_TOKEN` | 空 | OpenClaw 网关 Token |
| `CODEX_EXECUTABLE` | `codex` | codex CLI 可执行文件 |

`POSTGRES_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` 与 `AI_DEV_OS_POSTGRES_URL` 等旧变量等价，前者优先。

## 4. 数据库初始化

首次启动时，应用通过内置迁移运行器自动执行
`services/orchestrator/src/main/resources/db/migration/V*.sql`（Flyway 风格 SQL）：

- 已应用版本记录在 `schema_migrations` 表
- 迁移是幂等的（`CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`）
- 健康检查 `/actuator/health` 在迁移未完成前返回非 UP；就绪探针见 `/api/health/readiness`

手工初始化（可选）：

```bash
psql "$POSTGRES_URL" -f services/orchestrator/src/main/resources/db/migration/V1__repository_documents.sql
```

## 5. 备份恢复

### 备份

```bash
docker compose exec postgres pg_dump -U ai_dev_os -d ai_dev_os -Fc > ai_dev_os.dump
```

### 恢复

```bash
docker compose exec -T postgres pg_restore -U ai_dev_os -d ai_dev_os --clean --if-exists < ai_dev_os.dump
```

或使用 SQL 格式：

```bash
docker compose exec postgres pg_dump -U ai_dev_os -d ai_dev_os > ai_dev_os.sql
docker compose exec -T postgres psql -U ai_dev_os -d ai_dev_os < ai_dev_os.sql
```

## 6. 健康检查

- `GET /actuator/health`：应用 + 数据库健康（Actuator）
- `GET /api/health`：存活探针
- `GET /api/health/readiness`：就绪探针（迁移完成 + 启动完成）

## 7. 持久化模式切换

`aidevos.persistence.type` 支持：

- `in-memory`（默认，测试/开发）
- `postgresql`（生产）

切换仅影响 Repository 装配，Service / Controller 层不变。
