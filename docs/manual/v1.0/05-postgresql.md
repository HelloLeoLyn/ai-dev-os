# 05 PostgreSQL 数据库配置

本章配置 AI Dev OS 的持久化数据库 PostgreSQL。

> 前提：已完成 `03-docker.md`（Docker 方案）或具备可用的 PostgreSQL。

---

## 1. AI Dev OS PostgreSQL 作用说明

`services/orchestrator` 使用 PostgreSQL 持久化：

- 仓库文档与审计事件（V1～V2）
- 计划版本冻结、执行任务与协调器控制（V3～V7）
- Outbox 可靠投递（审计事件发布失败重试）

持久化模式：

| 模式 | 环境变量值 | 用途 |
| --- | --- | --- |
| `in-memory` | `in-memory` | 开发调试，数据不持久 |
| `postgresql` | `postgresql` | 生产/正式验证，数据持久化 |

应用启动时会自动按版本顺序应用 `classpath:/db/migration/V*.sql`
（V1～V7），并记录到 `schema_migrations` 表。

---

## 2. 部署方式

### 2.1 Docker PostgreSQL（推荐）

```bash
docker run -d \
  --name ai-dev-os-postgres \
  -e POSTGRES_DB=ai_dev_os \
  -e POSTGRES_USER=ai_dev_os \
  -e POSTGRES_PASSWORD=<强密码> \
  -p 5432:5432 \
  -v ai-dev-os-pgdata:/var/lib/postgresql/data \
  postgres:16
```

数据卷持久化，容器删除后数据保留。

### 2.2 本地 PostgreSQL（说明）

直接安装在 WSL2 内：

```bash
sudo apt install -y postgresql postgresql-client
sudo systemctl enable --now postgresql
```

特点：

- 无容器依赖，适合最小化环境
- 默认仅监听 `localhost`，与 AI Dev OS 默认连接串一致
- 升级与备份需自行维护

---

## 3. 创建数据库

### 3.1 容器方案

容器已通过环境变量创建数据库与用户，无需额外操作：

- 数据库：`ai_dev_os`
- 用户：`ai_dev_os`
- 密码：启动时 `POSTGRES_PASSWORD` 设置的值

### 3.2 本地方案

```bash
sudo -u postgres psql
```

执行：

```sql
CREATE USER ai_dev_os WITH PASSWORD '<强密码>';
CREATE DATABASE ai_dev_os OWNER ai_dev_os;
```

### 3.3 密码配置说明

- 使用强密码，生产环境严禁默认空密码
- 密码只通过环境变量注入，不写入代码或配置文件
- 密码含特殊字符时注意环境变量引号转义

---

## 4. 环境变量配置

AI Dev OS v1.0 实际生效的环境变量（与 `application.properties` 一致）：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_DEV_OS_PERSISTENCE_TYPE` | `in-memory` | 持久化模式，正式环境设为 `postgresql` |
| `AI_DEV_OS_POSTGRES_URL` | `jdbc:postgresql://localhost:5432/ai_dev_os` | JDBC 连接串（含 host/port/database） |
| `AI_DEV_OS_POSTGRES_USER` | `ai_dev_os` | 数据库用户 |
| `AI_DEV_OS_POSTGRES_PASSWORD` | 空 | 数据库密码，必须提供 |

> 说明：`AI_DEV_OS_POSTGRES_HOST` / `_PORT` / `_DATABASE` / `_USERNAME`
> 这类拆分变量在当前 v1.0 代码中未接线，实际以
> `AI_DEV_OS_POSTGRES_URL` 一个连接串变量为准。

启动示例：

```bash
export AI_DEV_OS_PERSISTENCE_TYPE=postgresql
export AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://localhost:5432/ai_dev_os
export AI_DEV_OS_POSTGRES_USER=ai_dev_os
export AI_DEV_OS_POSTGRES_PASSWORD='<强密码>'
```

---

## 5. 数据库验证

### 5.1 psql 连接

```bash
PGPASSWORD='<强密码>' psql -h localhost -p 5432 -U ai_dev_os -d ai_dev_os -c "SELECT version();"
```

预期：输出版本号与连接信息。

### 5.2 表检查

```bash
PGPASSWORD='<强密码>' psql -h localhost -U ai_dev_os -d ai_dev_os -c "\dt"
```

应用启动后应能看到迁移创建的表。

### 5.3 migration 验证

```sql
SELECT version, description, success
FROM schema_migrations
ORDER BY version;
```

预期：

- 记录 V1～V7
- `success` 均为 `true`
- 重复启动不会重复执行（幂等）

---

## 6. 与 Orchestrator 集成

启动顺序：

1. PostgreSQL 已启动且连接可用
2. 设置 `AI_DEV_OS_PERSISTENCE_TYPE=postgresql` 及相关连接变量
3. 启动 orchestrator（见 `11-service-start.md`）
4. 等待 `GET /api/health/readiness` 返回 `200 READY`

注意：

- 迁移未完成前 readiness 返回 `503 NOT_READY`
- 多实例部署共享同一数据库，由数据库原子性保证不双执行
- 运维细节（环境变量全表、故障恢复）见 `docs/operation/runbook.md`

---

## 7. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 连接失败 | 数据库未启动或网络不通 | 确认容器/服务运行，`psql` 直接连接测试 |
| 端口冲突（5432 被占用） | 本地已有 PostgreSQL | 检查 `ss -ltnp`，改映射端口并同步 `AI_DEV_OS_POSTGRES_URL` |
| 密码错误 | 环境变量与建库密码不一致 | 核对 `POSTGRES_PASSWORD` 与 `AI_DEV_OS_POSTGRES_PASSWORD` |
| 仍使用 in-memory | 未设置 `AI_DEV_OS_PERSISTENCE_TYPE` | 显式设为 `postgresql` 后重启 |
| migration 失败 | 权限不足或 SQL 冲突 | 确认用户对库有建表权限，检查 `schema_migrations` 中失败记录 |
| 无法连接 5432 | 容器未映射端口 | 检查 `docker ps` 的端口映射 `0.0.0.0:5432->5432` |
| 重启后数据丢失 | 容器未挂载数据卷 | 使用 `-v` 数据卷，不要用匿名容器运行 |
