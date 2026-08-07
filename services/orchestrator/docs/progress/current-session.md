# 当前开发状态

更新日期：2026-08-05

## 0. 本次会话结论：Phase 8-F 生产验证与运维门禁完成

Phase 8-A～8-E 已先后完成：可靠性持久化基础、Worker lease 生命周期、
Execution/restart recovery、PlanRun 可靠调度、Transactional Outbox。
本次会话完成 Phase 8-F 生产级验证与运维门禁，未新增大型功能、未重构核心
架构、未修改 Agent/Executor 行为、未引入新基础设施，未 commit、未 push。

### 新增 health/readiness 运维门禁

- 新增 `GET /api/health` 存活探针与 `GET /api/health/readiness` 就绪探针。
- readiness 在启动完成（`ApplicationReadyEvent`）且 PostgreSQL 迁移 V1～V7
  全部应用前保持 `503 NOT_READY`；就绪后返回 `200 READY`。
- `PostgresDocumentStore` 新增只读 `migrationsComplete()`，不改变写入路径。
- 修复既有装配缺陷：`OutboxRelay` 多构造函数缺少 `@Autowired`，PostgreSQL
  模式下 Spring 上下文此前无法实例化 relay；该缺陷由本次“完整启动关闭”
  验证发现并修复。

### PostgreSQL 全链路验证

- 新库初始化：V1～V7 全部应用，`schema_migrations` 记录 1～7，关键表、列
  （`jobs` lease/attempt/version/recovery 列、`audit_outbox` relay 控制列、
  `repository_documents.version`）与索引（`idx_audit_outbox_claim` 等）齐全。
- 旧库升级：仅 V1～V4 的 Phase 7-era 数据库带真实数据升级到 V1～V7，数据
  保留、V5～V7 补齐、V7 默认值回填、重复迁移幂等。
- 双实例并发：两个实例并发 `claimNext` 同一 Job 恰好一个成功；并发
  `claimCoordinator` 同一 PlanRun 恰好一个 owner；两个 `PlanScheduler` 并发
  reconcile 只创建确定 ID 的 step Job 一次。
- Worker kill -9：lease 过期后 `LeaseReaper` 将 Job 标记 `RECOVERY_REQUIRED`
  （`LEASE_EXPIRED`、`recovery_count+1`、lease 清除）；旧实例 renew/complete
  被 fencing 拒绝；`RECOVERY_REQUIRED` 不会被自动重新 claim。
- Outbox relay 停止后恢复：relay 停止期间行保持 pending；重启后全部发布且
  恰好一次；consumer 失败崩溃后新 relay 按退避恢复，无重复。
- Postgres 模式完整启动关闭：`@SpringBootTest` + Testcontainers PostgreSQL
  完整上下文启动（迁移应用、readiness 就绪、健康端点 200），并验证独立
  启动的应用上下文优雅关闭。

### 文档

- 新增 `docs/operation/runbook.md`：启动顺序与配置、健康检查、双实例部署、
  kill -9 / outbox / scheduler 故障恢复流程、升级与回滚、告警查询 SQL。
- 更新 `docs/development/run-guide.md`：PostgreSQL 模式启动与健康端点说明。

### 验证结果

- 全量回归：`mvn test` 413 项（基线 396 + 新增 17），0 failure、0 error、
  1 skipped（依赖外部 filesystem MCP 的既有可选项）。

本文记录 AI Dev OS Orchestrator 当前仓库实现状态，以及本次会话实际验证的本机运行环境。仓库能力与外部 OpenClaw Browser Runtime 状态分开描述。

## 0.1 v0.6.0 Phase 6-A 里程碑（历史）

Phase 1～5 已完成，当前代码已经形成以下能力链：

- Orchestrator、Task/Job、AgentResolver 和 ExecutionEngine。
- OpenClaw Browser Agent 与 Screenshot Artifact。
- Secure Codex Agent、Workspace/Sandbox、Coding Approval 和 Git Artifact。
- Tool Core、MCP Client、确定性 Tool Execution 和 Tool Approval。
- Hermes Plan Model、Planner SPI、Plan Approval、顺序 PlanScheduler 和 Replanning。

Phase 6-A 已在隔离临时 Git 仓库完成真实 Coding 子链验收：

```text
WAITING_APPROVAL → APPROVED → CONSUMED → RUNNING → SUCCESS
```

验收确认 Codex 在 `workspace-write` 下修改 tracked 文件并运行测试；Git diff Artifact、
ExecutionRecord、approvalId、Codex threadId、workspace、sandbox、before/after HEAD 均完整。
HEAD 前后保持一致，没有自动 commit 或 push。真实 filesystem MCP read-only 验收通过；
OpenClaw Gateway、browser tool、Chrome CDP 和 Screenshot 输出也分别通过真实探测。

当前已知集成缺口：

1. `HermesPlanner` 当前仅生成固定单 Step，尚不能生成 Browser → MCP → Coder → Tester 多 Agent Plan。
2. OpenClaw Browser 对临时 localhost 登录页返回 `browser navigation blocked by policy`；全局安全策略未被绕过或关闭。

下一阶段 Phase 6-B 将实现可验证的串行多 Agent Plan，并继续复用现有 Plan Approval、
Coding Approval、Tool Approval 和 PlanScheduler，不引入并行或自动审批。localhost 测试许可
需要先完成独立的最小范围安全设计与确认。

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
| Maven artifact | `com.aidevos:orchestrator:1.2.2` |
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

### Phase 7-A Persistence（待 Review，未提交）

- 新增协议化 Repository 边界，覆盖 Task、Agent、Job、ExecutionRecord、Schedule、Coding/Tool/Plan Approval、ReplanRequest 与 PlanRun。
- 默认继续使用 InMemory Repository，保持既有 API 和测试构造方式；通过 `aidevos.persistence.type=postgresql` 显式切换 PostgreSQL。
- PostgreSQL 使用 JSONB 文档表和版本化迁移脚本，Plan Approval 与 PlanRun 的冻结/唯一性规则保持不变。
- Job、Approval、PlanRun/StepRun/Attempt 使用显式状态快照恢复；Schedule 会在启动时恢复持久化定义。
- 不持久化进程句柄、registry、MCP session、OpenClaw pending request 或本机设备身份/认证信息。
- 本阶段未修改 Planner、ExecutionEngine、AgentExecutor 或执行/审批决策逻辑；未 commit、未 push。

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
