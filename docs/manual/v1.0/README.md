# AI Dev OS v1.0 搭建手册

## 1. 手册目的

本手册面向在 Windows 11 + WSL2 环境中从零搭建 AI Dev OS v1.0 的开发者。

目标：

- 提供从操作系统环境到服务跑通的完整搭建路径
- 覆盖环境准备、工具链、Agent、MCP、部署、启动与验证
- 每个环节给出可执行的命令和验证方式

范围边界：

- 本手册只描述「如何从零搭建并跑通」
- 运行维护与故障排查分别由 operation 与 troubleshooting 文档负责
- 系统架构设计由 architecture 文档负责

---

## 2. 阅读顺序

按以下章节顺序阅读：

```text
01 Windows 11
  ↓
02 WSL2 + Ubuntu
  ↓
03 Docker
  ↓
04 工具链（Java / Maven / Node）
  ↓
05 PostgreSQL
  ↓
06 Codex CLI
  ↓
07 DeepSeek 模型切换
  ↓
08 OpenClaw
  ↓
09 MCP 配置
  ↓
10 AI Dev OS 部署
  ↓
11 服务启动
  ↓
12 验证流程
  ↓
13 常见问题
  ↓
99 附录（版本矩阵 / 端口 / 环境变量）
```

阅读建议：

- 首次搭建：按 01～12 顺序完整执行
- 已具备部分环境：直接跳转到对应章节
- 仅补充 Agent 配置：阅读 06～09
- 仅部署与启动：阅读 10～12

> 章节文档逐步补充中，README 为手册入口。

---

## 3. 与 architecture / operation / troubleshooting 文档关系

| 文档目录 | 回答的问题 | 与本手册的关系 |
| --- | --- | --- |
| `docs/architecture` | 系统是什么、为什么这样设计 | 搭建前了解整体设计 |
| `docs/operation` | 运行中如何维护（启动、健康检查、恢复） | 手册 11/12 章引用，避免重复 |
| `docs/troubleshooting` | 出问题如何排查 | 手册 13 章引用，补充搭建期问题 |
| `docs/manual/v1.0` | 如何从零搭建并跑通 | 本手册 |

复用原则：

- 部署、启动、健康检查等运行细节以 `docs/operation/runbook.md` 为准
- 常见运行故障以 `docs/troubleshooting/common-errors.md` 为准
- 本手册只保留搭建步骤，与上述文档通过链接关联，不复制内容

历史说明：

- `docs/manual/AI-Dev-OS搭建手册.md` 为历史文档，内容与
  `docs/architecture/system-design.md` 重复，暂不删除
- v1.0 手册以本目录 `docs/manual/v1.0/` 为最新入口

---

## 4. v1.0 环境范围

| 组件 | 版本 / 配置 | 说明 |
| --- | --- | --- |
| 操作系统 | Windows 11（22H2 及以上） | 宿主机 |
| 虚拟化 | WSL2 + Ubuntu 22.04/24.04 LTS | 开发运行环境 |
| 容器 | Docker（WSL2 后端） | 环境与依赖容器化 |
| Java | 21（Temurin / OpenJDK） | 后端运行时 |
| 构建 | Maven 3.9+（或 `mvnw`） | 后端构建 |
| 前端 | Node 20/22 LTS + npm | Vue 3 + Vite 构建 |
| 数据库 | PostgreSQL（本地或容器） | 库 `ai_dev_os`，用户 `ai_dev_os` |
| AI 客户端 | Codex CLI | 支持切换 DeepSeek 模型 |
| 自动化 | OpenClaw gateway | 默认 `ws://127.0.0.1:18789` |
| MCP | `.mcp/config.json` + `permissions` | 工具能力接入 |
| 服务 | `services/orchestrator` | 后端 `18080`，前端 `15174` |
| 持久化 | `AI_DEV_OS_PERSISTENCE_TYPE` | 生产 `postgresql`，开发 `in-memory` |

网络要求：

- 可访问 OpenAI / DeepSeek API
- 可访问 Maven Central 与 npm registry
