# 09 MCP 配置与工具能力层

本章配置 MCP（Model Context Protocol）工具能力层，
为 Agent 提供文件、Git、Docker、浏览器等工具。

> 前提：已完成 `06-codex-cli.md` 与 `08-openclaw.md`。

---

## 1. MCP 在 AI Dev OS 中的作用

### 1.1 MCP 是什么

MCP（Model Context Protocol）是模型与外部工具之间的标准化协议：

- Server：提供工具能力（文件、Git、Docker 等）
- Client：Agent（Codex / OpenClaw）按标准方式调用工具

### 1.2 为什么需要 MCP

- Agent 不直接操作外部资源，通过 MCP 统一调用
- 权限可在工具层统一管控
- 能力可插拔，按需接入新工具

### 1.3 Agent 与 MCP 的关系

```text
Agent（决策）
  ↓ 调用
MCP Client
  ↓ 标准化协议
MCP Server（文件 / Git / Docker / 浏览器）
  ↓
外部资源
```

AI Dev OS 原则（见 `docs/operation/agent-workflow.md`）：

- Agent 负责思考与决策
- MCP 负责能力调用
- 用户保留最终控制权

---

## 2. 当前 MCP 能力

| 能力 | 用途 | 典型工具 |
| --- | --- | --- |
| Filesystem | 文件读取、搜索、修改 | read_file、write_file、search_files |
| Git | 版本管理 | status、log、diff、commit |
| Docker | 容器与环境管理 | ps、logs、run、stop |
| Browser | 页面访问与验证 | 打开页面、点击、截图 |

> 权限策略已在 `.mcp/permissions.md` 中定义；实际 server 注册
> 以 `.mcp/config.json` 为准（见第 7 节现状说明）。

---

## 3. MCP 配置文件

AI Dev OS 使用项目级 `.mcp/config.json`：

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "mcp-server-filesystem",
      "args": [
        "/home/administrator/workspace/ai-dev-os"
      ]
    }
  }
}
```

结构说明：

| 字段 | 说明 |
| --- | --- |
| `mcpServers` | server 集合，key 为 server 名称 |
| `command` | 启动 server 的可执行命令 |
| `args` | 传给 server 的参数（如文件系统根路径） |

修改后需要重启使用该配置的 Agent / orchestrator 才生效。

---

## 4. Codex MCP 配置关系

Codex 在 `~/.codex/config.toml` 中声明 MCP 服务器：

```toml
[mcp_servers.filesystem]
command = "mcp-server-filesystem"
args = ["/home/administrator/workspace/ai-dev-os"]
```

关系说明：

- `.mcp/config.json`：AI Dev OS 项目统一的 MCP 清单
- `~/.codex/config.toml`：Codex 本地的 MCP 接入配置
- 两处配置需保持一致，server 名称与参数对应

> Codex 的完整 MCP 配置方式以官方文档为准；
> AI Dev OS 以 `.mcp/config.json` 为统一入口。

---

## 5. 权限设计

依据 `.mcp/permissions.md`：

默认原则：

- 默认最小权限
- 允许：查询信息、读取文件、分析状态
- 不允许：未授权修改、删除资源、修改系统配置

三级权限：

| 级别 | 示例操作 |
| --- | --- |
| 默认允许 | `read_file`、`list_directory`、`git status`、`docker ps` |
| 需要确认 | `write_file`、`git commit`、`docker run` |
| 禁止自动执行 | 删除文件、`git reset`/`git clean`、删除容器/镜像、权限提升 |

以下情况必须人工确认：

- 修改代码 / 配置
- 数据删除
- 发布部署
- 权限提升

---

## 6. 启用与验证

orchestrator 侧开关：

- `tools.mcp.enabled=false`（当前默认关闭）
- 需要 MCP 工具时显式开启：

```bash
export TOOLS_MCP_ENABLED=true
# 或按部署方式设置对应配置项
```

### 6.1 MCP server 启动检查

```bash
# 确认 server 命令可用
which mcp-server-filesystem

# 确认 server 能启动（示例）
mcp-server-filesystem --help
```

### 6.2 工具列表检查

- 在 Codex 中启动后查看可用工具/服务器
- 确认 filesystem 等 server 已连接，工具正常注册

预期：目标 server 处于 connected 状态，对应工具可用。

---

## 7. 当前实际配置说明

### 7.1 filesystem

- 已在 `.mcp/config.json` 注册
- 根路径：`/home/administrator/workspace/ai-dev-os`
- 建议仅开放项目工作区，避免暴露整个文件系统

### 7.2 git

- 权限策略已定义（只读默认、commit 需确认、reset 危险）
- server 未在 `.mcp/config.json` 注册，按需接入
- 接入时确保 server 指向仓库目录

### 7.3 docker

- 权限策略已定义（ps/logs 允许、run/stop 需确认、删除危险）
- server 未在 `.mcp/config.json` 注册，按需接入
- 前提：当前用户可访问 docker daemon（见 `03-docker.md` 第 4 节）

---

## 8. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| MCP server 启动失败 | 命令不存在或依赖缺失 | `which <command>` 检查，安装对应 server 包 |
| 权限错误 | 操作超出允许范围 | 检查 `.mcp/permissions.md`，危险操作需人工确认 |
| server 找不到 | 未注册或配置名不一致 | 核对 `.mcp/config.json` 与 Codex `[mcp_servers]` |
| 修改后不生效 | 未重启 Agent / orchestrator | 重启后重新加载配置 |
| Docker MCP 不可用 | daemon 不可访问或 server 未接入 | 确认 `docker ps` 可用，接入 docker server |
| filesystem 路径错误 | 根路径不存在或越权 | 核对 `args` 路径，仅开放项目工作区 |
| 工具未出现在列表 | `tools.mcp.enabled` 未开启 | 显式开启后重启并重新检查工具列表 |
