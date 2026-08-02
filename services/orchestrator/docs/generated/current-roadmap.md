# 基于当前代码的能力盘点

> 本文中的“已完成”“已存在但未完善”“缺失”只表示当前代码可观察状态，不表示产品承诺或计划。缺失项通过当前生产源码和资源文件中不存在对应实现来判断。

## 1. 已完成能力

### Task 基础管理

- `TaskLoader` 能从 `classpath:/tasks/*.json` 加载 Task。
- `TaskController` 提供创建和列表 API。
- `TaskManager` 提供注册、查询、列表、删除和状态更新方法。

限制说明：这里的“完成”指基础内存管理链路可用，不表示已持久化或已做完整校验。

### 同步 Task 执行

- `ExecutionController.execute` 根据 Task ID 调用 `ExecutionEngine.execute`。
- Engine 完成 Agent 解析、Context 构造、Executor 调用、Result 返回和 ExecutionRecord 保存。
- Agent 解析失败和 Executor `Exception` 会转换为失败结果。

### 异步 Job 与编码审批闭环

- `JobService` 创建 Task 快照和 ExecutionJob。
- `JobWorker` 使用有界队列和单线程 Worker。
- Job 状态支持 QUEUED、RUNNING、WAITING_APPROVAL、SUCCESS、FAILED。
- Job 能关联 ExecutionRecord ID。
- API 支持提交、按 ID 查询和按状态列表查询。
- workspace-write 编码任务支持审批请求、批准后重新入队和拒绝结束；重新入队失败会恢复等待状态。

### Agent 注册与解析

- YAML Agent 配置加载和启动注册链已存在。
- 支持显式 agentName 和 requiredCapabilities 两种解析方式。
- 支持 enabled、capability 和 Executor 存在性校验。

### Executor 扩展点

- `AgentExecutor` 接口、`ExecutorRegistry` 和 `ExecutorManager` 已存在。
- Spring Bean 自动注册 Executor。
- 已有 Mock、Codex、OpenClaw 三种实现。
- Agent 的 Executor 专属配置通过 executorConfig/parameters 隔离。

### OpenClaw Gateway 适配

- WebSocket 连接和 Protocol v4 connect 握手已实现。
- token、设备 identity、签名、operator scopes 已接入 connect 请求。
- 支持 request ID 关联、超时和错误响应。
- Agent 调用实现 `agent → agent.wait → chat.history` 闭环。

### ExecutionRecord 查询

- 执行后生成 ExecutionRecord 和 ExecutionReport。
- 提供摘要列表、状态/Task 过滤和详情 API。
- 编码执行记录包含 Workspace、Sandbox、Approval、Git before/after、退出码、Codex threadId、起止时间和内嵌 Artifact。

### Schedule 与 Dashboard 基础能力

- Schedule 支持 Cron/时区校验、注册、列表、删除和触发 Job。
- Dashboard 聚合 Task、Job、ExecutionRecord 的内存统计和近期 Job。
- 前端已存在 Dashboard、Task、Job、Agent、Schedule 和 ExecutionRecord 页面或路由。

### 命令执行基础设施

- `CommandExecutor` 支持工作目录、stdout/stderr 捕获和退出码。
- `ConfigurableCommandPolicy` 支持 ALLOW、DENY、REQUIRE_APPROVAL 规则。
- `GitExecutor` 提供 status 和 diff 命令封装。

## 2. 已存在但未完善的能力

### Artifact

`ExecutionResult.artifacts` 和 `ExecutionArtifact` 模型已存在。Browser 参数分支能映射截图等 Artifact；Codex 能生成 Git 状态、tracked/cached diff、untracked 列表与安全受限的文件内容、事件流和结果 Artifact，并随 ExecutionRecord 查询。仍没有独立 Artifact repository、下载 API 或对象存储。

### ExecutionContext 追踪字段

ExecutionContext 的 executionId、jobId、metadata 和 parameters 已进入生产执行链；后续仍需持久化和跨进程恢复。

### Executor 异常闭环

Executor 抛出的 `Exception` 已转换并保存失败记录。但 Engine 不捕获 `Error`，也不统一处理记录保存或结果构建阶段的异常；JobWorker 对剩余 `Throwable` 只更新 Job，不能保证这类情况已有 ExecutionRecord。

### Agent capability 选择

Capability 匹配已工作，但 capability 是字符串列表。Selector 返回注册顺序中第一个匹配项；它不在候选阶段过滤 disabled Agent 或检查 Executor 可用性。

### Agent 描述与权限字段

AgentDefinition 已有 type、description、permissionLevel，但 Resolver 和 CommandPolicy 不使用这些字段。permissionLevel 当前不产生执行权限效果。

### Codex Executor

Codex Executor 已支持可配置 CLI 路径、正确的非交互 stdin EOF、approval policy、Workspace 允许根、Git 仓库校验、`read-only`/`workspace-write` Sandbox、写审批、硬超时、输出 schema、结构化摘要和 Artifact。仍缺少主动取消、实时流式事件和持久化 Artifact。

### OpenClaw Executor

OpenClaw 调用闭环已存在，但 `OpenClawExecutor.execute` 使用 `join()` 同步等待。普通结果仍提取 assistant 文本；Browser 参数分支能构造 browser tool 指令并从约定 JSON 文本映射截图等 Artifact。runId/sessionKey 仍未写入 ExecutionResult metadata，Artifact 映射也依赖 Agent 遵守返回约定。

### Command Approval

通用命令策略和 `ApprovalGate` 仍默认关闭；Coder Agent 另有 Task 级 Approval Controller 和内存审批流程。审批没有持久化、鉴权或操作者身份审计。

### Git 诊断

Codex Executor 通过 `GitInspector` 采集执行前后状态、分支、HEAD、diff、cached diff 和 NUL 分隔的 untracked 路径；ExecutionEngine 将关键内容和 approvalId 写入 ExecutionReport/ExecutionRecord。该诊断目前是 Codex 专属，不是所有 Executor 的通用步骤。

### 内存存储

Task、Agent、Job、ExecutionRecord、运行时 Schedule 都有 Store/Manager，但均以内存集合保存。当前没有数据库 Repository，也没有进程重启恢复逻辑。

### 并发执行

Job 队列是有界的，但 Worker 固定为单线程。没有动态并发度、按 Agent 限流或分布式消费。

### Task 校验

Task JSON 解析和 API 注册已存在，但没有 Bean Validation 或独立 schema 校验。TaskManager 接受 null/重复 ID 等输入的行为未由生产层统一约束。

### 前端 Artifact 展示

当前前端 `ExecutionResult` 类型和执行详情主要围绕 output/report；没有按 Artifact type 展示文件、截图、测试报告或日志的组件。

## 3. 当前代码中缺失的能力

以下能力在当前 `src/main/java` 和 `src/main/resources` 中没有对应生产实现：

### Hermes Planner

- 没有 Hermes client、Executor、配置或模型。
- 当前名为 `planner` 的 Agent 使用 `mock` Executor。

### MCP Tool Layer

- 没有 MCP client、tool registry、tool gateway 或 MCP Executor。
- 当前 AgentExecutor 没有工具发现/调用接口。

### 独立 Browser Agent 实现

- 没有 `BrowserExecutor` 或浏览器驱动客户端。
- 当前 `browser-agent` 是映射到 OpenClaw agentId `main` 的配置项。
- 已有的 Phase 2-B 最小接入刻意复用该链路：Task 的 `parameters.browser` 支持六种 action，并由 OpenClawExecutor 生成指令和映射结果。

### 持久化

- 没有数据库依赖、JPA/JDBC Repository、迁移脚本或外部 Job Queue。
- Job、ExecutionRecord 和运行时 Task 不会跨进程恢复。

### Job 控制

- 没有取消、暂停、恢复、重试、重放或幂等提交 API。
- ExecutionJob 状态没有 CANCELLED、RETRYING 等值。

### 流式执行事件

- AgentExecutor 返回同步 `ExecutionResult`。
- 没有 Executor progress、日志流、SSE 或 WebSocket 执行事件 API。

### Artifact 存储与下载

- 没有 Artifact repository、对象存储、文件上传/下载 API、生命周期清理或访问控制。

### 结构化 Planner 工作流

- 没有 Plan、PlanStep、依赖图或多 Agent 编排模型。
- 一次 ExecutionEngine 调用只解析并调用一个 AgentExecutor。

### Executor 健康与路由

- 没有 Executor health check、负载信息、优先级或多候选路由策略。

## 4. 代码现状与目标式设计表述的主要差异

以下差异可直接从当前实现观察：

1. **不是单一的“API → Task 创建 → Job 执行”链。** Task 创建、同步执行和 Job 提交是三个独立 API；同步执行不创建 ExecutionJob。
2. **Task、Job、ExecutionRecord 不是统一持久化执行模型。** 三者是独立对象，通过 taskId 和 executionRecordId 关联，并保存在不同内存容器中。
3. **Planner 名称不代表 Planner 实现。** `planner` 当前映射 `mock`，没有 Hermes 或结构化计划模型。
4. **Browser Agent 是配置角色，不是独立 Browser Executor。** 当前 browser-agent 通过 OpenClaw 执行。
5. **Codex 已存在，但仍是 CLI 包装。** 它构造本地命令并把 stdout/stderr 映射为文本结果。
6. **MCP Tool Layer 尚不存在。** 当前 Executor 不能通过统一 Tool Gateway 调用工具。
7. **Artifact 仍不是完整结果子系统。** Browser OpenClaw 分支可在本次响应中生产 Artifact，但当前没有保存、查询、下载或展示链。
8. **Git 诊断模型仍在，但不属于当前通用执行步骤。** GitExecutor 和报告字段存在，ExecutionEngine 不调用它们。
9. **异步存在于 Job 和 OpenClaw 底层，但 Executor SPI 是同步的。** JobWorker 在线程中调用同步 Engine，OpenClawExecutor 对 CompletableFuture 使用 join。
10. **运行状态主要是进程内状态。** 当前实现适合单实例运行验证，不具备代码层面的跨实例或重启恢复能力。
