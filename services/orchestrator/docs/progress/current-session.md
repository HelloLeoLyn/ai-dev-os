# 当前开发状态

更新日期：2026-08-02

本文记录 AI Dev OS Orchestrator 当前仓库实现状态，以及本次会话实际验证的本机运行环境。仓库能力与外部 OpenClaw Browser Runtime 状态分开描述。

## 1. 当前完成阶段

### Orchestrator

- Task、Agent、Executor、ExecutionRecord 和异步 Job 的基础执行闭环已经存在。
- Phase 1.5-A 已体现在当前代码中：`ExecutionResult` 支持 Artifact，`ExecutionContext` 包含 `executionId`、`jobId`、`metadata` 和 `parameters`。
- Phase 1.5-B 已体现在当前代码中：Executor 异常可转换为失败 `ExecutionResult`，Agent 的 Executor 专属配置已从通用字段中隔离，`ExecutionEngine` 不再执行固定 Git 诊断。
- 当前架构反向文档已经生成在 `docs/generated/`。

### Browser Runtime

- Phase 2-A 已完成运行环境打通。
- OpenClaw Gateway 能通过 CDP 连接独立的 Windows Chrome。
- `main` Agent 已被授予 Agent 级 `browser` tool 权限。
- 已通过 OpenClaw Browser CLI 和 `main` Agent 的真实 browser tool 调用访问 `https://example.com`，获取标题、正文和截图。
- Browser Runtime 尚未作为 Orchestrator 内部的独立 capability 或 Executor 接入。

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

下一阶段为 Browser Agent 接入 Orchestrator，建议保持现有执行链稳定并分步完成：

1. 定义 Orchestrator 可表达的 Browser 操作与结果边界。
2. 明确 Task 参数如何传递 browser action、URL、定位信息和断言条件。
3. 复用现有 `browser-agent → OpenClawExecutor → main Agent → browser tool` 链路完成最小闭环。
4. 将截图等 Browser 产物映射为 `ExecutionArtifact`。
5. 为 Browser Task 增加单元测试和真实环境验收，同时保持现有 OpenClaw 协议、认证和 Task 执行流程不变。

当前不应一次性引入 Hermes Planner、MCP Tool Layer 或新的 Playwright 控制链。

## 5. 未完成问题

### Browser Agent

- 仓库中没有独立 `BrowserExecutor` 或 Browser capability 实现。
- `browser-agent` 当前仍是映射到 OpenClaw `main` Agent 的配置角色。
- `openclaw-test.json` 的描述只要求返回浏览器状态，尚未形成结构化 navigate、snapshot、click、input、screenshot 或 assert Task 模型。
- 已打通的 Windows Chrome、防火墙和 OpenClaw Browser profile 属于本机运行配置，尚未被仓库部署配置管理。

### Artifact

- Artifact 模型已经存在，但当前 Mock、Codex 和 OpenClaw Executor 没有生成 Artifact。
- 没有 Artifact repository、文件下载 API、生命周期管理或访问控制。
- 前端尚未按截图、文件、测试报告或日志类型展示 Artifact。

### ExecutionContext 与执行可靠性

- `executionId`、`jobId` 和 metadata 尚未在当前生产执行链中完整赋值。
- Executor 的 `Exception` 已统一转换，但 `Error`、记录保存失败及结果构建失败不在同一保证范围内。
- Executor SPI 仍是同步返回；OpenClawExecutor 内部使用 `join()` 等待异步调用。

### Agent 与 Executor

- Capability 仍使用字符串列表，没有独立 Capability 模型。
- AgentSelector 使用注册顺序选择首个匹配项，没有健康度、负载或优先级路由。
- `permissionLevel` 尚未连接 CommandPolicy 或实际执行权限。
- CodexExecutor 当前是本地 CLI 包装，缺少结构化输出、取消、流式日志和 Artifact 映射。

### 后续目标能力

- Hermes Planner 尚无 Client、Executor、配置或结构化 Plan 模型；当前 `planner` 使用 Mock Executor。
- MCP Tool Layer 尚无 Client、Tool Registry、Gateway 或 MCPExecutor。
- Task、Agent、Job、ExecutionRecord 和 Schedule 运行状态主要保存在内存中，进程重启后不能恢复。
- Job 尚不支持取消、暂停、恢复、重试、重放和幂等提交。
- 没有 Executor progress、日志流或执行事件 API。

## 6. 本次记录范围

- 本次仅新增此 Markdown 文件。
- 未修改 Orchestrator Java 代码、前端代码、资源配置或测试。
- 未修改 OpenClaw 配置和 Windows Browser Runtime。
