# AI Dev OS 生产运维指南

版本： v1.2.2

适用服务：`services/orchestrator`（Spring Boot 4 / Java 21 / Maven / Docker）

---

# 1. 生产启动流程

## 1.1 前置条件

- Java 21 与 Maven 3.9+，或使用 `services/orchestrator` 的 Docker 镜像。
- PostgreSQL（生产模式必需），版本 15+。
- 可选：OpenClaw Gateway（`ws://127.0.0.1:18789`）与本地 Codex CLI。

## 1.2 配置环境变量

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_DEV_OS_PERSISTENCE_TYPE` | `in-memory` | 生产必须设为 `postgresql` |
| `AI_DEV_OS_POSTGRES_URL` | `jdbc:postgresql://localhost:5432/ai_dev_os` | JDBC 连接串 |
| `AI_DEV_OS_POSTGRES_USER` | `ai_dev_os` | 数据库用户 |
| `AI_DEV_OS_POSTGRES_PASSWORD` | 空 | 数据库密码，生产必须提供 |
| `AI_DEV_OS_WORKER_ID` | 随机 | 建议显式设置稳定 ID |
| `AI_DEV_OS_WORKER_LEASE_DURATION` | `30m` | Job lease 时长 |
| `AI_DEV_OS_WORKER_HEARTBEAT_INTERVAL` | lease/3 | heartbeat 间隔 |
| `AI_DEV_OS_OUTBOX_RELAY_INTERVAL` | `1s` | outbox relay 轮询周期 |

## 1.3 启动步骤

```bash
cd services/orchestrator
# 生产模式（PostgreSQL）
AI_DEV_OS_PERSISTENCE_TYPE=postgresql \
AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://localhost:5432/ai_dev_os \
AI_DEV_OS_POSTGRES_USER=ai_dev_os \
AI_DEV_OS_POSTGRES_PASSWORD='***' \
mvn spring-boot:run
```

前端（可选）：

```bash
cd services/orchestrator/frontend
npm run build
# 将 dist/ 交由 Nginx 等静态服务器托管，并代理 /api 到后端 18080
```

## 1.4 启动自检

应用启动时会运行 `StartupValidator`：

- PostgreSQL 连接与 Migration 版本检查（仅生产模式）。
- Agent / Skill / MCP Plugin 配置完整性检查。

任一检查失败将中止启动并输出明确错误，例如：

```text
Startup validation failed: PostgreSQL connection or schema migrations are not ready
Startup validation failed: no agents configured (agents.yaml)
```

---

# 2. 配置检查

## 2.1 配置文件清单

| 文件 | 内容 |
| --- | --- |
| `src/main/resources/agents.yaml` | Agent 定义、能力、版本、Skill 绑定 |
| `src/main/resources/skills.yaml` | Skill 定义与工具列表 |
| `src/main/resources/mcp-plugins.yaml` | MCP 插件与权限级别 |
| `src/main/resources/agents-market.yaml` | Agent 市场包目录 |
| `src/main/resources/models.yaml` | 模型提供方与路由 |

## 2.2 配置自检

```bash
# 健康检查：存活与就绪
curl -s http://127.0.0.1:18080/api/health
curl -s http://127.0.0.1:18080/api/health/readiness

# 就绪详情（含组件状态）
curl -s http://127.0.0.1:18080/api/health/readiness | jq .details.components

# 运行指标
curl -s http://127.0.0.1:18080/api/metrics
```

`components` 字段说明：

| 组件 | 含义 |
| --- | --- |
| `database` | PostgreSQL 连接与迁移状态（in-memory 模式为 `in-memory`） |
| `migration` | Schema 迁移是否 complete |
| `agentRegistry` | Agent 注册表是否已加载（up/down） |
| `mcpRegistry` | MCP 插件注册表是否已加载（up/down） |
| `skillRegistry` | Skill 注册表是否已加载（up/down） |

---

# 3. 故障排查

## 3.1 启动失败

| 症状 | 排查 |
| --- | --- |
| 启动报 PostgreSQL 迁移未完成 | 确认数据库可达、`schema_migrations` 完整；查看迁移错误日志 |
| 启动报无 Agent / Skill / Plugin 配置 | 检查对应 YAML 文件是否存在且内容非空 |
| 就绪探针 503 | 检查 `startupComplete` 与 `migrations` 状态；迁移完成后自动恢复 |

## 3.2 运行期问题

| 症状 | 排查 |
| --- | --- |
| Job 卡在 RECOVERY_REQUIRED | 查看 lease 过期与 `lease_reaper` 日志，确认 worker heartbeat |
| 审计事件缺失 | 检查 outbox relay 日志与 `audit_outbox` 死信 |
| 指标异常 | 调用 `GET /api/metrics` 对比各注册表数量 |

## 3.3 常用诊断

```bash
# 查看迁移版本
SELECT version, description, success FROM schema_migrations ORDER BY version;

# 查看待重试的 outbox 消息
SELECT * FROM audit_outbox WHERE published_at IS NULL ORDER BY created_at;
```

---

# 4. 数据备份建议

## 4.1 备份内容

生产数据全部位于 PostgreSQL，包含：

- `jobs` / `execution_attempts`：执行状态与结果。
- `memory_records`：项目记忆（规则、历史、BUG_RECORD）。
- `projects`：多项目元数据。
- `skills` / `agent_packages` / `mcp_plugins`：注册表状态。
- `audit_events` / `audit_outbox`：审计轨迹。

## 4.2 备份方式

```bash
# 逻辑备份（推荐定期执行）
pg_dump -h localhost -U ai_dev_os -Fc -f ai-dev-os-$(date +%Y%m%d).dump ai_dev_os

# 恢复
pg_restore -h localhost -U ai_dev_os -d ai_dev_os ai-dev-os-YYYYMMDD.dump
```

## 4.3 建议

- 每日全量备份 + 启用 WAL 归档实现时间点恢复。
- 备份保留期建议 ≥ 30 天，并定期演练恢复。
- 升级前先备份；升级后核对 `schema_migrations` 与健康检查。
