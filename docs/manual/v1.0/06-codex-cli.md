# 06 Codex CLI 配置

本章配置 Codex CLI。Codex 是 AI Dev OS 中的代码执行 Agent，
负责编写、修改、重构代码与修复 Bug。

> 前提：已完成 `04-toolchain.md`（Node/npm 可用）。

---

## 1. Codex CLI 作用说明

### 1.1 在 AI Dev OS 中的角色

- 编写 / 修改 / 重构代码
- 修复 Bug 并按计划执行任务
- 通过 orchestrator 的 `coding.codex.*` 配置被调用

orchestrator 相关配置（`application.properties`）：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coding.codex.executable` | `codex` | Codex 可执行文件 |
| `coding.codex.approval-policy` | `never` | 自动化调用时的审批策略 |
| `coding.codex.timeout` | `10m` | 单次执行超时 |

### 1.2 与 Hermes / OpenClaw 的关系

- Hermes：理解需求、拆分任务、制定计划（不直接改代码）
- Codex：按确认后的计划执行代码修改
- OpenClaw：浏览器自动化 / GUI 操作 / 测试执行

协作流程：

```text
需求 → Hermes 计划 → 人工确认 → Codex 改代码 / OpenClaw 执行操作 → 测试 → 报告
```

---

## 2. 安装

```bash
npm install -g @openai/codex
```

版本验证：

```bash
codex --version
```

预期：输出 Codex CLI 版本号。

> 如 `npm install` 超时，先确认 `npm config get registry`
> （见 `04-toolchain.md`），再重试。

---

## 3. 登录认证

```bash
codex login
```

按提示完成认证（浏览器登录 OpenAI 账号）。

`~/.codex` 目录说明：

```text
~/.codex/
├── config.toml    # 全局配置（模型、项目信任、MCP 等）
├── auth.json      # 登录凭证（请勿提交到仓库）
├── sessions/      # 会话历史
└── log/           # 运行日志
```

注意：

- `auth.json` 包含敏感凭证，不纳入版本控制
- 多账号切换可删除 `auth.json` 后重新 `codex login`

---

## 4. 项目配置

### 4.1 config.toml

配置文件位置：

- 全局：`~/.codex/config.toml`
- 项目级：项目目录下 `.codex/config.toml`（可选，覆盖全局）

示例（`~/.codex/config.toml`）：

```toml
model = "gpt-5.6"
model_provider = "openai"
approval_policy = "never"
```

### 4.2 projects trust_level

按项目目录设置信任级别：

```toml
[projects]
"~/workspace/ai-dev-os" = { trust_level = "trusted" }
```

信任级别说明：

| 级别 | 行为 |
| --- | --- |
| `trusted` | 信任目录，允许执行 |
| `explicit` | 每次操作需明确确认 |
| `untrusted` | 不信任，禁止执行 |

> 首次在项目目录启动时，也可按提示交互式确认信任。

### 4.3 MCP 配置关系

- Codex 通过 MCP 服务器获得工具能力（文件、Git 等）
- MCP 服务器在 `config.toml` 的 `[mcp_servers]` 中声明
- AI Dev OS 的 MCP 配置详见 `09-mcp.md`（`.mcp/config.json` 与 `tools.mcp.enabled`）

---

## 5. 模型配置

### 5.1 OpenAI 模型

默认使用 OpenAI 模型：

```toml
model = "gpt-5.6"
model_provider = "openai"
```

### 5.2 自定义 model provider

```toml
[model_providers.my_provider]
name = "My Provider"
base_url = "https://api.example.com/v1"
env_key = "MY_PROVIDER_API_KEY"
wire_api = "chat"
```

使用时切换：

```toml
model = "my-model-name"
model_provider = "my_provider"
```

要点：

- `base_url`：兼容 OpenAI 协议的服务地址
- `env_key`：API Key 对应的环境变量名
- `wire_api`：`chat`（OpenAI 兼容）或 `responses`（原生）

### 5.3 DeepSeek 配置入口

- DeepSeek 属于自定义 model provider 场景
- 具体配置（base_url、模型名、API Key）见下一章 `07-deepseek.md`

---

## 6. 使用方式

### 6.1 进入项目目录

```bash
cd ~/workspace/ai-dev-os
```

### 6.2 启动 Codex

```bash
codex
```

交互式对话，或直接给任务：

```bash
codex "分析当前仓库结构并输出报告"
```

### 6.3 profile 切换

使用不同配置组合：

```toml
# ~/.codex/config.toml
[profiles.deepseek]
model = "deepseek-chat"
model_provider = "deepseek"
approval_policy = "on-request"
```

切换：

```bash
codex --profile deepseek
```

---

## 7. 安全建议

- 按信任边界设置 `trust_level`，未知项目使用 `untrusted` 或 `explicit`
- 保持审批策略有效：交互式使用建议 `on-request`，未确认不修改
- AI Dev OS 规范禁止未经确认直接修改代码（见 `AGENTS.md` 与
  `docs/operation/agent-workflow.md`）
- 凭证安全：`auth.json`、API Key 不提交仓库，使用环境变量注入
- 工作区限制：orchestrator 的 `coding.workspace.allowed-roots`
  限定可写目录，默认仅项目根目录

---

## 8. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `codex: command not found` | npm 全局 bin 未加入 PATH | 检查 npm 全局路径并加入 `~/.bashrc` |
| 登录失败 | 浏览器无法打开或网络受限 | 重试 `codex login`，检查网络/代理 |
| 模型调用 401/403 | 凭证无效或 provider 配置错误 | 重新登录，核对 `env_key` 与 API Key |
| 模型 404 | provider 不支持该模型名 | 核对 `base_url` 与模型名 |
| 项目被拒绝执行 | trust_level 未放行 | 在 `config.toml` 设置 `trusted` 或交互式确认 |
| 自动化调用超时 | 任务超过 `coding.codex.timeout` | 调大 timeout 或拆分任务 |
| 配置不生效 | 改错配置文件 | 确认修改的是 `~/.codex/config.toml` 或项目 `.codex/config.toml` |
| 凭证被提交到仓库 | auth.json 未忽略 | 移出仓库并加入 `.gitignore` |
