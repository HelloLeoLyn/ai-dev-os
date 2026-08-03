# AI Dev OS Orchestrator 当前架构

> 生成依据：当前工作区中的 `src/main`、`frontend/src`、`scripts` 和 `pom.xml`。本文描述代码已经实现的结构和调用关系，不代表目标架构。

## 1. 技术与部署结构

- 后端：Java 21、Spring Boot 4.0.0、Maven。
- 前端：Vue 3、TypeScript、Vite。
- 后端默认端口：`18080`。
- 前端开发端口：`15174`，通过 Vite 将 `/api` 代理到后端。
- OpenClaw 默认地址：`ws://127.0.0.1:18789`。
- 数据保存：Task、Job、ExecutionRecord 和 Schedule 注册信息均保存在进程内存中。

代码依据：`pom.xml`、`frontend/vite.config.ts`、`src/main/resources/application.properties`、`TaskManager`、`JobStore`、`ExecutionRecordManager`、`ScheduleService`。

## 2. 当前模块结构

| 模块 | 主要包或目录 | 当前职责 |
| --- | --- | --- |
| API | `controller` | Task、Job、Approval、Agent、ExecutionRecord、Schedule、Dashboard HTTP API |
| Task | `task`、`model/TaskDefinition` | JSON Task 加载、注册、查询、状态与通用 parameters 字段维护 |
| Job | `job` | Task 快照、内存队列、单 Worker 执行和 Job 状态维护 |
| Agent | `agent`、`manager/AgentManager` | Agent 注册、按名称或 capability 解析 |
| Executor | `executor` | Executor SPI、注册表、Mock/Codex/OpenClaw 实现 |
| Execution | `execution` | 上下文构造、Executor 调用、结果和执行记录 |
| OpenClaw | `openclaw` | Protocol v4 WebSocket 客户端、握手、请求关联和 Agent 调用适配 |
| Command | `executor/command` | 本地进程执行、命令策略和审批判断 |
| Git | `executor/git` | Git 仓库校验与执行前后 status、branch、HEAD、diff、cached diff 快照 |
| Workspace | `execution/workspace` | 真实路径解析、允许根目录约束和 Git 仓库校验 |
| Approval | `approval` | workspace-write 编码任务的内存审批请求、批准消费和拒绝 |
| Schedule | `schedule` | Cron 注册、触发和 Job 提交 |
| Dashboard | `dashboard` | Task、Job、ExecutionRecord 的内存统计聚合 |
| Frontend | `frontend/src` | Dashboard、Task、Job、Agent、Schedule 和执行记录页面 |
| 配置 | `src/main/resources` | Agent YAML、Task JSON、Spring/OpenClaw/Job 配置 |

### 2.1 Phase 4～5 新增模块

| 模块 | 主要包 | 当前职责 |
| --- | --- | --- |
| Tool Core | `tool` | ToolDefinition、ToolInvocation、ToolRegistry、ToolRouter 和 Artifact 映射 |
| MCP Client | `tool/mcp` | stdio Session、initialize、tools/list、tools/call 和错误转换 |
| Tool Approval | `tool/approval` | ALLOW/DENY/REQUIRE_APPROVAL 下的独立工具审批审计 |
| Plan Model | `plan` | 不可变 Plan、PlanStep、Dependency、Snapshot 和 DAG Validator |
| Planner | `planner` | Planner SPI、Hermes/Fake Planner、PlanDraft 和 PlannerService |
| Plan Approval | `plan/approval` | Plan 版本哈希冻结、人工批准/拒绝和审计 |
| Plan Run | `plan/run`、`plan/schedule` | PlanRun、StepRun、StepAttempt、串行 DAG 调度和 Artifact 成功门槛 |
| Replanning | `planner/replan` | 失败分类、ReplanRequest、新版本校验和重新审批标记 |

当前规划执行主链：

```text
User Request → Planner → PlanDraft → PlanValidator → Plan Approval
→ PlanScheduler → StepTaskFactory → JobService → ExecutionEngine
→ AgentResolver → AgentExecutor → Artifact / ExecutionRecord
```

Phase 6-A 已真实验证其中 Coding 子链；完整多 Agent Plan 留待 Phase 6-B。

## 3. 核心调用链

当前存在同步执行和异步 Job 执行两条入口，两者最终调用同一个 `ExecutionEngine.execute`。

```mermaid
flowchart LR
    SyncAPI["ExecutionController.execute"] --> Engine["ExecutionEngine.execute"]
    JobAPI["JobController.submit"] --> Submit["JobService.submit"]
    Submit --> Queue["JobWorker.submit"]
    Queue --> Worker["JobWorker.execute"]
    Worker --> Engine
    Engine --> Resolve["AgentResolver.resolve"]
    Resolve --> Executor["AgentExecutor.execute"]
    Engine --> Save["ExecutionRecordManager.save"]
    Executor --> Result["ExecutionResult"]
    Save --> Record["ExecutionRecord"]
```

同步入口直接返回 `ExecutionResult`。异步入口先返回 `JobSubmissionResponse`，客户端再通过 `/api/jobs/{id}` 查询 `ExecutionJob`。

## 4. Task 执行流程

### 4.1 Task 来源

Task 有两种注册来源：

1. `TaskLoader.run/loadTasks` 扫描 `classpath:/tasks/*.json`，反序列化为 `TaskDefinition` 后调用 `TaskManager.register`。
2. `POST /api/tasks` 由 `TaskController.register` 直接调用 `TaskManager.register`。

`TaskManager` 使用 `LinkedHashMap<String, TaskDefinition>` 保存 Task；相同 ID 会覆盖已有对象。Task 可通过可选 `parameters` Map 向执行链传递参数；异步 Job 创建 Task 快照时会递归复制 Map/List 参数。

### 4.2 同步执行

```mermaid
sequenceDiagram
    participant Client
    participant EC as ExecutionController
    participant TM as TaskManager
    participant EE as ExecutionEngine
    participant AR as AgentResolver
    participant AE as AgentExecutor
    participant ERM as ExecutionRecordManager

    Client->>EC: POST /api/tasks/{id}/execute
    EC->>TM: getTask(id)
    TM-->>EC: TaskDefinition / null
    EC->>EE: execute(taskDefinition)
    EE->>AR: resolve(taskDefinition)
    AR-->>EE: ResolvedAgent
    EE->>AE: execute(executionContext)
    AE-->>EE: ExecutionResult
    EE->>ERM: save(executionRecord)
    EE-->>EC: ExecutionResult
    EC-->>Client: 200 + ExecutionResult
```

Task 不存在时返回 404。Agent 解析失败或 Executor 抛出 `Exception` 时，`ExecutionEngine` 构造失败 `ExecutionResult`，并继续保存 `FAILED` ExecutionRecord。

### 4.3 异步 Job 执行

`JobService.submit` 复制 Task 字段形成快照，创建 `ExecutionJob`，先保存到 `JobStore`，再通过 `JobWorker.submit` 放入有界队列。队列满时删除刚保存的 Job，并由 Controller 返回 HTTP 429。

`JobWorker` 使用单线程 `ExecutorService` 和 `ArrayBlockingQueue`。Worker 将 Job 标记为 RUNNING，通过 `ExecutionRecordManager.capture` 调用 `ExecutionEngine.execute`。编码任务需要写权限时会进入 `WAITING_APPROVAL`；批准后重新入队并消费一次性审批，拒绝则结束为 FAILED。正常执行后取得本次保存的 ExecutionRecord ID，最后标记为 SUCCESS 或 FAILED。

## 5. Agent 流程

启动阶段：

```mermaid
flowchart LR
    YAML["agents.yaml"] --> Load["AgentConfigLoader.loadAgents"]
    Load --> Init["AgentInitializer.run"]
    Init --> Register["AgentManager.register"]
```

执行阶段：

```mermaid
flowchart TD
    Resolve["AgentResolver.resolve"] --> HasName{"TaskDefinition.agentName?"}
    HasName -- Yes --> ByName["AgentManager.getAgent"]
    HasName -- No --> Select["AgentSelector.select"]
    Select --> All["AgentManager.getAllAgents"]
    ByName --> Validate["validateEnabled / validateCapabilities"]
    All --> Validate
    Validate --> GetExecutor["ExecutorManager.getExecutor"]
    GetExecutor --> Resolved["ResolvedAgent"]
```

显式 `agentName` 优先；没有名称时，Selector 按 Agent 注册顺序返回第一个包含全部 required capabilities 的 Agent。

## 6. Executor 流程

`AgentExecutor` 当前定义两个方法：`getType()` 和同步的 `execute(ExecutionContext)`。

Spring 将全部 `AgentExecutor` Bean 注入 `ExecutorRegistry`。Registry 以 `getType()` 返回值为键，重复键会抛出 `IllegalStateException`。`ExecutorManager` 先按 Agent 名称取得 `AgentDefinition.executor`，再从 Registry 取得实现。

当前实现：

- `MockAgentExecutor`：返回模拟文本。
- `CodexExecutor`：校验 Workspace 和 Sandbox，执行审批门，采集 Git 前后快照，并通过带硬超时的 `CommandExecutor` 执行可配置路径的 Codex CLI。命令显式传入 approval policy、workspace、sandbox、model、JSON 和 output schema；CommandExecutor 在启动后关闭 stdin，保留 stdout/stderr、退出码、硬超时和进程树终止。结果转换为摘要、Git/Codex Artifact 和审计 metadata。
- `OpenClawExecutor`：读取 `agentId` 参数并调用 `OpenClawTaskService`；当参数包含 `browser` Map 时，通过 `BrowserTaskPromptBuilder` 构造 browser tool 指令，并由 `BrowserResultMapper` 将约定 JSON 结果中的截图等条目映射为 `ExecutionArtifact`。非 Browser Task 保持原有文本调用和结果映射。

`ExecutionEngine.createContext` 填充 executionId、jobId、Task/Agent 文本字段、进程工作目录、Task parameters 和 Agent executorConfig。执行记录保存 workspace、sandbox、approvalId、branch、before/after HEAD、exitCode、Codex threadId 和起止时间等审计字段。

Task parameters 会先写入 `ExecutionContext.parameters`，随后叠加 Agent executorConfig，因此 Task 不能覆盖 `agentId` 等 Executor 配置。

### 6.1 Browser Task 参数边界

Browser Task 复用 OpenClaw Executor，不存在独立 BrowserExecutor。参数位于 `parameters.browser`，当前支持 `navigate`、`snapshot`、`click`、`input`、`screenshot` 和 `assert` action；URL、定位信息、输入值、断言和截图选项作为该 Map 的附加字段透传到确定性 Agent 指令。

Browser Agent 被要求只通过已有 browser tool 执行，并以 `output` 与 `artifacts` 组成的 JSON 对象返回最终结果。无法解析该对象时，Executor 保留原始 assistant 文本并返回空 Artifact 列表，以兼容现有 OpenClaw Agent 行为。

真实环境已通过 `POST /api/tasks/openclaw-test/execute` 验证该链路：OpenClaw `main` Agent 导航到 `https://example.com`，确认标题 `Example Domain`，生成 PNG 截图，并由 Orchestrator 映射为 `type=screenshot`、`mediaType=image/png` 的 `ExecutionArtifact`。

## 7. OpenClaw 调用流程

```mermaid
sequenceDiagram
    participant EE as ExecutionEngine.execute
    participant OE as OpenClawExecutor.execute
    participant OTS as OpenClawTaskService.execute
    participant OC as OpenClawClient
    participant GW as OpenClaw Gateway

    EE->>OE: execute(context)
    OE->>OTS: execute(OpenClawTaskRequest)
    OTS->>OC: request("agent", agentId/message/idempotencyKey)
    OC->>GW: GatewayRequest
    GW-->>OC: runId + sessionKey
    OTS->>OC: request("agent.wait", runId/timeoutMs)
    OC->>GW: GatewayRequest
    GW-->>OC: status
    alt status == ok
        OTS->>OC: request("chat.history", sessionKey)
        OC->>GW: GatewayRequest
        GW-->>OC: messages
        OTS-->>OE: OpenClawTaskResult(ok, assistant text)
    else error / timeout / pending
        OTS-->>OE: OpenClawTaskResult(status, null)
    end
    OE-->>EE: ExecutionResult
```

`OpenClawWebSocketClient.connect` 建立 WebSocket，收到 `connect.challenge` 后由 `sendConnectRequest` 发送 Protocol v4 connect 请求。连接请求包含 operator role、read/write scopes、token 和设备签名。普通 Gateway 请求通过 UUID 关联 `pendingRequests`，并受 request timeout 控制。

## 8. 当前存储与生命周期

Phase 7-A 将业务状态统一抽象为 Repository。Task、Agent、Job、ExecutionRecord、Schedule、Coding/Tool/Plan Approval、ReplanRequest 和 PlanRun 均保留默认 InMemory 实现，并可通过 `aidevos.persistence.type=postgresql` 切换到 PostgreSQL JSONB 文档实现。数据库连接只读取 `AI_DEV_OS_POSTGRES_URL`、`AI_DEV_OS_POSTGRES_USER` 和 `AI_DEV_OS_POSTGRES_PASSWORD`，默认模式不会连接数据库。

PostgreSQL 使用版本化迁移脚本创建 `repository_documents`，以 repository type 与 entity ID 为主键；PlanRun 对 approval ID 建立唯一索引。状态机对象通过显式 snapshot/restore 恢复，Approval、Job、PlanRun/StepRun/Attempt 的当前状态与审计时间不会因反序列化退回初始值。Schedule 启动时会重新注册已持久化定义。

进程期对象仍有意不持久化：TaskScheduler 的 `ScheduledFuture`、Tool/Executor/Planner registry、MCP session、OpenClaw pending request，以及含本机身份信息的 OpenClaw device identity。Artifact 仍内嵌于 ExecutionRecord，尚无独立对象存储或下载 API。已持久化的 QUEUED/RUNNING Job 在进程重启后的自动接管属于后续恢复调度能力，不在本阶段改变现有执行流程。
