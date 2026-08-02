# 当前开发状态

更新日期：2026-08-02

本文记录 AI Dev OS Orchestrator 当前仓库实现状态，以及本次会话实际验证的本机运行环境。仓库能力与外部 OpenClaw Browser Runtime 状态分开描述。

## 1. 当前完成阶段

### Orchestrator

- Task、Agent、Executor、ExecutionRecord 和异步 Job 的基础执行闭环已经存在。
- Phase 1.5-A 已体现在当前代码中：`ExecutionResult` 支持 Artifact，`ExecutionContext` 包含 `executionId`、`jobId`、`metadata` 和 `parameters`。
- Phase 1.5-B 已体现在当前代码中：Executor 异常可转换为失败 `ExecutionResult`，Agent 的 Executor 专属配置已从通用字段中隔离，`ExecutionEngine` 不再执行固定 Git 诊断。
- 当前架构反向文档已经生成在 `docs/generated/`。
- Phase 2-B 最小 Browser Agent 接入已体现在当前代码中：Task 可携带 `parameters.browser`，并复用 `browser-agent → OpenClawExecutor → main Agent → browser tool` 链路执行。
- Browser 结果可按约定 JSON 映射为 `ExecutionResult.output` 和截图等 `ExecutionArtifact`；非 Browser OpenClaw 任务保持原行为。
- Phase 3 Coder Agent 已完成产品级最小闭环：受限 Workspace、显式 Sandbox、workspace-write 审批、批准后恢复 Job、可配置 Codex CLI、正确 stdin EOF、Git 前后证据、tracked/staged/untracked Artifact、ExecutionRecord 审计字段、结构化输出 schema 和硬超时。
- `danger-full-access` 被明确拒绝；`read-only` 不触发写审批，`workspace-write` 默认需要审批。

### Browser Runtime

- Phase 2-A 已完成运行环境打通。
- OpenClaw Gateway 能通过 CDP 连接独立的 Windows Chrome。
- `main` Agent 已被授予 Agent 级 `browser` tool 权限。
- 已通过 OpenClaw Browser CLI 和 `main` Agent 的真实 browser tool 调用访问 `https://example.com`，获取标题、正文和截图。
- Browser Runtime 没有作为独立 Executor 接入；Orchestrator 通过既有 OpenClaw Executor 和 `browser` capability 复用该 Runtime。

## 2. 已完成功能

### Task 与 Job

- 从 `classpath:/tasks/*.json` 加载 Task。
- Task 注册、查询、列表、删除和状态更新。
- 同步 Task 执行入口。
- 有界队列、单 Worker 的异步 Job 执行。
- Job 状态包含 `QUEUED`、`RUNNING`、`SUCCESS` 和 `FAILED`。
- Job 与 ExecutionRecord 关联。

### Agent 系统

- 从 `agents.yaml` 加载并注册 Agent。
- 支持按显式 Agent 名称或 required capabilities 解析 Agent。
- 解析过程检查 Agent enabled 状态、capability 和 Executor 是否存在。
- 当前配置的 Agent 包含 `planner`、`executor`、`coder`、`tester` 和 `browser-agent`。

### Executor 系统

- `AgentExecutor`、`ExecutorRegistry` 和 `ExecutorManager` 扩展结构已经存在。
- 当前实现包含 `MockAgentExecutor`、`CodexExecutor` 和 `OpenClawExecutor`。
- Spring Bean Executor 可自动注册。
- Executor 专属参数通过 `AgentDefinition.executorConfig` 隔离；YAML 中使用 `codex`、`openclaw` 等独立配置块。
- Executor 抛出的 `Exception` 会转换为失败结果，并进入 ExecutionRecord 生成链。

### Execution 与 Result

- `ExecutionContext` 包含 workspace、executionId、jobId、metadata 和 parameters。
- `ExecutionResult` 包含 success、message、output 和 artifacts。
- `ExecutionArtifact` 可表达 Artifact 基础信息。
- 执行后生成 `ExecutionRecord` 和 `ExecutionReport`。
- 提供 ExecutionRecord 摘要、过滤和详情查询 API。

### OpenClaw 集成

- WebSocket Client 和 Protocol v4 connect 握手已经实现。
- Gateway token、device identity、签名和 operator scopes 已接入握手请求。
- 支持 Gateway request ID 关联、超时和错误响应。
- OpenClaw Agent 调用实现 `agent`、`agent.wait`、`chat.history` 闭环。
- `browser-agent` 当前通过 `OpenClawExecutor` 映射到 OpenClaw `main` Agent。
- Browser Task 支持 `navigate`、`snapshot`、`click`、`input`、`screenshot` 和 `assert` action，参数统一放在 `parameters.browser` 下。
- `openclaw-test.json` 已更新为访问 `https://example.com`、断言标题并请求截图的最小验收 Task。
- 已通过 Orchestrator 同步执行 API 完成真实端到端验收：导航和标题断言成功，返回的 PNG 截图被映射为 `ExecutionArtifact`。

### 其他基础能力

- Schedule 的 Cron/时区校验、注册、删除和 Job 触发。
- Dashboard 的 Task、Job、ExecutionRecord 内存统计。
- Dashboard、Task、Job、Agent、Schedule 和 ExecutionRecord 前端页面或路由。
- CommandExecutor、命令策略、ApprovalGate 基础类和 GitExecutor 封装。

## 3. 当前运行环境

### 仓库技术栈

| 项目 | 当前值 |
| --- | --- |
| Java | 21；本机实测 OpenJDK 21.0.11 |
| Spring Boot | 4.0.0 |
| Maven artifact | `com.aidevos:orchestrator:0.0.1-SNAPSHOT` |
| Node.js | 本机实测 v24.18.0 |
| 后端开发端口 | 18080 |
| 前端开发端口 | 15174 |
| OpenClaw Gateway | `ws://127.0.0.1:18789` |

本次检查时，Orchestrator 后端 18080 和前端 15174 未监听；OpenClaw Gateway 18789 正在监听且 health 检查通过。

### OpenClaw Browser Runtime

以下内容是本机外部运行环境，不属于 Orchestrator 仓库配置：

| 项目 | 当前状态 |
| --- | --- |
| Gateway Agent | `main` |
| Browser profile | `windows-chrome` |
| Browser driver | `openclaw` |
| Transport | `cdp` |
| Attach mode | `attachOnly: true` |
| CDP URL | `http://172.27.0.1:9223` |
| Windows Chrome profile | `C:\openclaw-browser-profile` |
| CDP 状态 | `running`、`cdpReady`、`cdpHttp`、`pageReady` 均为 true |
| Browser tool 授权 | 仅 `main.tools.alsoAllow: [browser]` |

Windows 防火墙仅允许 WSL2 子网 `172.27.0.0/20` 访问 TCP 9223。开发/临时调试用途的 9222 环境保持独立，未作为 AI Dev OS 固定 Browser Runtime。

## 4. 下一步任务

Phase 2-B 与 Phase 3 最小闭环均已完成真实验收。后续应保持当前边界，不提前进入 Phase 4；Phase 3 可选增强包括：

1. 审批持久化、鉴权和操作者身份审计。
2. 主动取消与实时流式执行事件。
3. 独立 Artifact repository 和下载/生命周期管理。
4. 保持 Workspace、Sandbox、Approval 和 Git 证据边界不变。

当前不应一次性引入 Hermes Planner、MCP Tool Layer 或新的 Playwright 控制链。

## 5. 未完成问题

### Browser Agent

- 仓库中没有独立 `BrowserExecutor`；这是 Phase 2-B 复用现有 OpenClaw 执行链的既定选择。
- `browser-agent` 仍是映射到 OpenClaw `main` Agent 的配置角色。
- Browser 参数边界、最小验收 Task、单元测试和 Orchestrator 到 Windows Chrome 的真实 navigate/断言/截图验收已经完成；其余 action 尚未逐项做真实环境验收。
- 已打通的 Windows Chrome、防火墙和 OpenClaw Browser profile 属于本机运行配置，尚未被仓库部署配置管理。

### Artifact

- Artifact 模型已经存在，Browser OpenClaw 分支可映射截图，Codex 可生成 Git 与执行结果 Artifact；普通 OpenClaw 和 Mock 仍不生成 Artifact。
- 没有 Artifact repository、文件下载 API、生命周期管理或访问控制。
- 前端可查看 ExecutionRecord 的基础 Artifact 数据，但尚未按截图、文件、测试报告或日志类型提供专用展示/下载。

### ExecutionContext 与执行可靠性

- `executionId`、`jobId` 和编码审计 metadata 已接入生产执行链，但均为内存记录，重启不可恢复。
- Executor 的 `Exception` 已统一转换，但 `Error`、记录保存失败及结果构建失败不在同一保证范围内。
- Executor SPI 仍是同步返回；OpenClawExecutor 内部使用 `join()` 等待异步调用。

### Agent 与 Executor

- Capability 仍使用字符串列表，没有独立 Capability 模型。
- AgentSelector 使用注册顺序选择首个匹配项，没有健康度、负载或优先级路由。
- `permissionLevel` 尚未连接 CommandPolicy 或实际执行权限。
- CodexExecutor 已有可配置 CLI、非交互 stdin EOF、结构化输出、硬超时和 Artifact 映射，仍缺少主动取消与实时流式日志。

### 后续目标能力

- Hermes Planner 尚无 Client、Executor、配置或结构化 Plan 模型；当前 `planner` 使用 Mock Executor。
- MCP Tool Layer 尚无 Client、Tool Registry、Gateway 或 MCPExecutor。
- Task、Agent、Job、ExecutionRecord 和 Schedule 运行状态主要保存在内存中，进程重启后不能恢复。
- Job 尚不支持取消、暂停、恢复、重试、重放和幂等提交。
- 没有 Executor progress、日志流或执行事件 API。

## 6. 本次记录范围

- 新增 Browser 指令构造和结果映射组件。
- 扩展 Task parameters、ExecutionContext 参数合并和异步 Job 参数快照。
- 更新 Browser 验收 Task、前端 Task 类型和相关单元测试。
- Phase 2-B 基线后继续实现 Phase 3 Coder Agent 控制面、审计和结果链。
- 后端完整测试 156 个全部通过；Phase 3 收尾定向测试 44 个通过；前端类型检查和生产构建通过。
- 真实审批门验证通过：批准前 Job 为 WAITING_APPROVAL、审批为 PENDING、Git HEAD/工作树不变；批准后成功恢复为 RUNNING。
- Codex CLI 无事件的根因已确认为调用方未关闭 stdin；项目内 CommandExecutor 现会提供 EOF，无需 `/tmp` 包装器。真实 tracked 与 untracked 两轮均完成 `WAITING_APPROVAL → APPROVED → RUNNING → SUCCESS`，审批最终 CONSUMED，ExecutionRecord 保存 approvalId、exitCode 和 threadId，HEAD 未变化且无自动 commit/push。
- tracked 轮生成非空 diff Artifact；untracked 轮生成文件列表与 UTF-8 内容 Artifact，并具备大小限制、二进制判断、越界符号链接拒绝和安全截断。
- 真实 `openclaw-test` 执行成功：output 确认 example.com 标题断言通过，Artifact 为 1878×1917、35687 字节的 PNG 截图。
- 未修改 OpenClaw 配置、Windows Browser Runtime、防火墙或 CDP 配置；验收后已停止本次临时启动的 Orchestrator 后端。
