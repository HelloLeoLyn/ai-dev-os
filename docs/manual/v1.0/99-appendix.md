# 99 附录

本附录汇总 AI Dev OS v1.0 搭建手册涉及的环境基线、
端口、环境变量、项目目录与文档索引，供快速查阅。

---

## 1. AI Dev OS v1.0 版本矩阵

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Windows | 11 22H2 及以上 | 宿主机 |
| WSL2 | 2.x | 默认版本必须为 2 |
| Ubuntu | 22.04 / 24.04 LTS | 开发运行环境 |
| Docker | 最新稳定版 | Docker Desktop + WSL2 或 Docker Engine |
| Java | 21 | Temurin / OpenJDK |
| Maven | 3.9.x | 或使用项目 `mvnw` |
| Node | 24.x | 前端构建 |
| npm | 11.x | 随 Node 安装 |
| PostgreSQL | 16（推荐） | 库 `ai_dev_os`，用户 `ai_dev_os` |
| Codex | 最新稳定版 | `npm install -g @openai/codex` |
| OpenClaw | 最新稳定版 | Gateway 模式 |
| MCP | 标准协议 | `.mcp/config.json` 注册 server |

> 实际依赖版本以 `services/orchestrator/pom.xml`（`java.version=21`）
> 与 `services/orchestrator/frontend/package.json` 声明为准。

---

## 2. 端口清单

| 端口 | 服务 | 说明 |
| --- | --- | --- |
| `18080` | 后端 | orchestrator API（`server.port=18080`） |
| `15174` | 前端 | Vite dev server（`--port 15174`） |
| `5432` | PostgreSQL | 数据库（默认 `localhost:5432`） |
| `18789` | OpenClaw Gateway | WebSocket（`ws://127.0.0.1:18789`） |

> Dockerfile 中 `EXPOSE 8080` 为历史遗留，与实际 `18080` 不一致，
> 容器部署需显式映射 `-p 18080:18080`。

---

## 3. 环境变量清单

### 3.1 持久化

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_DEV_OS_PERSISTENCE_TYPE` | `in-memory` | 生产必须为 `postgresql` |

### 3.2 PostgreSQL（AI_DEV_OS_POSTGRES_*）

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_DEV_OS_POSTGRES_URL` | `jdbc:postgresql://localhost:5432/ai_dev_os` | JDBC 连接串 |
| `AI_DEV_OS_POSTGRES_USER` | `ai_dev_os` | 数据库用户 |
| `AI_DEV_OS_POSTGRES_PASSWORD` | 空 | 数据库密码，必须提供 |

> v1.0 使用 URL 单一变量；`HOST` / `PORT` / `DATABASE` / `USERNAME`
> 拆分变量未接线。

### 3.3 OpenClaw

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OPENCLAW_GATEWAY_URL` | `ws://127.0.0.1:18789` | Gateway 地址 |
| `OPENCLAW_GATEWAY_TOKEN` | 本地默认值 | 生产必须覆盖为强随机值 |

### 3.4 Codex 相关变量

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CODEX_EXECUTABLE` | `codex` | Codex 可执行文件 |
| `CODEX_APPROVAL_POLICY` | `never` | 自动化审批策略 |
| `CODEX_EXECUTION_TIMEOUT` | `10m` | 单次执行超时 |
| `DEEPSEEK_API_KEY` | 无 | DeepSeek API Key（自定义 provider） |

---

## 4. 项目目录说明

```text
ai-dev-os/
├── services/
│   └── orchestrator/          # 后端服务（Spring Boot 4 / Java 21）
│       ├── src/main/          # 后端源码与配置（application*.properties）
│       ├── frontend/          # 前端（Vue 3 + Vite，Node 24）
│       ├── Dockerfile         # 后端容器镜像（多阶段构建）
│       ├── pom.xml            # Maven 构建
│       └── scripts/           # 启动脚本（start-all / backend / frontend）
├── docs/                      # 文档（architecture / operation / manual / troubleshooting）
├── configs/                   # Agent 与工作流配置（codex / hermes / openclaw / tester）
├── .mcp/                      # MCP 配置与权限（config.json / permissions.md）
└── scripts/                   # 仓库根脚本目录（当前为空，占位）
```

说明：

- `services/orchestrator`：核心后端，含构建、启动脚本与容器化
- `frontend`：位于 `services/orchestrator/frontend`，独立构建
- `docs`：四类文档（见第 5 节）
- `configs`：Agent 角色与执行工作流配置
- `.mcp`：MCP 服务器注册与权限策略

---

## 5. 文档索引

| 目录 | 回答的问题 | 使用时机 |
| --- | --- | --- |
| `docs/architecture` | 为什么这样设计 | 了解系统设计 |
| `docs/operation` | 如何运行维护 | 运行期维护、故障恢复 |
| `docs/manual` | 如何从零搭建 | 环境准备与部署（本手册） |
| `docs/troubleshooting` | 如何排查问题 | 故障排查 |

推荐路径：

```text
搭建：docs/manual/v1.0/README.md（按章节顺序）
运维：docs/operation/runbook.md + agent-workflow.md
排查：docs/troubleshooting/common-errors.md + manual/v1.0/13-faq.md
设计：docs/architecture/
```
