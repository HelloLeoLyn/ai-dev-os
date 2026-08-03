

› 确认 Phase 7 基线提交。

  下一步进入 Phase 8 前，先进行只读设计分析。

  目标：
  Production Reliability。

  请分析：

  1. Job/Execution/PlanRun 当前恢复缺口
  2. Worker Lease 设计
  3. Restart Recovery 方案
  4. Outbox Transaction 一致性优化方案
  5. Phase 8 分阶段实施计划

  不要修改代码。
  等待确认后执行。


• 我会先用只读命令确认 Phase 7 基线（工作树状态、最近提交与相关标记），再检索 Job、Execution、PlanRun、Worker、Outbox 的实现和数据库迁移。命令仅读取 Git 元数据与源码，不会修改文件、启动服务或写入数据库。完成后我会给出恢复缺口、设计方案和 Phase 8 分阶段计划，停在确认点。

• Ran git status --short && git log -12 --oneline --decorate && rg -n --hidden -S "Phase 7|phase 7|Phase7|Job|Execution|PlanRun|Lease|Outbox|outbox" --glob '!target/**' --glob '!.git/**' .
  └ b41428c (HEAD -> main) feat: complete phase 7 observability platform baseline
    101cdb0 feat: add audit timeline console and observability platform
    … +1465 lines (ctrl + t to view transcript)
    ./services/orchestrator/src/main/java/com/aidevos/orchestrator/plan/run/PlanRun.java:53:        status = PlanRunStatus.REPLAN_REQUIRED;
    ./services/orchestrator/src/main/java/com/aidevos/orchestrator/plan/run/PlanRun.java:65:    public synchronized PlanRunStatus getStatus() { return status; }

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• Phase 7 基线已确认：main 当前 HEAD 为 b41428c feat: complete phase 7 observability platform baseline，工作树干净；README 与会话记录也明确标注 Phase 7 完成。下一步我会只读查看持久化仓储、JobWorker/JobService、ExecutionEngine、PlanScheduler、状态模型、迁移和启动配置，重
  点核对“已有持久化”与“真正可恢复执行”之间的差距；无文件或运行时状态变更。

• Explored
  └ List main
    Search (job|execution|plan/run|plan/schedule|persistence|audit|migration|application)
    Read ExecutionJob.java, JobWorker.java, JobService.java, JobRepository.java, JobStore.java, JobStatus.java

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• 已核实核心问题之一：Job 状态虽能持久化，但队列本身仅存在进程内存，启动时没有扫描与重投；RUNNING 也没有 owner、lease、heartbeat、attempt/fencing 信息，因此无法区分“仍在执行”与“进程已死”。我继续只读核对 PostgreSQL 文档快照、Outbox 发布边界及 PlanRun 调度时序，判断事务断
  点和重复执行窗口。

• Explored
  └ Read PostgresStateRepositories.java, PersistenceSnapshots.java, PostgresDocumentStore.java, PostgresAuditRepository.java, V4__audit_outbox.sql, PlanScheduler.java, PlanRun.java, StepRun.java, StepAttempt.java, ExecutionEngine.java, ExecutionRecordManager.java

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• 只读分析已形成主要结论：当前系统具备“状态快照持久化”，尚不具备“执行所有权与崩溃恢复协议”。此外，PlanScheduler 存在两个关键双写窗口：StepAttempt 开始 ↔ Job 创建/绑定，以及 PlanRun 保存 ↔ 审批消费；Outbox 也仍是审计内部事务，而非业务状态事务的一部分。我最后提取精确行号
  并检查现有配置/测试是否已有隐藏的恢复机制，然后给出可确认的 Phase 8 计划；仍不会执行测试或修改代码。

• Ran rg -n "class JobWorker|BlockingQueue|submit\(|markRunning|@PostConstruct|save\(job\)|shutdownNow" services/orchestrator/src/main/java/com/aidevos/orchestrator/job/JobWorker.java services/orchestrator/src/main/java/com/aidevos/orchestrator/job/JobService.java; rg
  │ -n "record Job|class PostgresJobRepository|void save\(ExecutionJob|put\(|getByStatus" services/orchestrator/src/main/java/com/aidevos/orchestrator/persistence/postgresql/PersistenceSnapshots.java services/orchestrator/src/main/java/com/aidevos/orchestrator/
  │ persistence/postgresql/PostgresStateRepositories.java services/orchestrator/src/main/java/com/aidevos/orchestrator/persistence/postgresql/PostgresDocumentStore.java; rg -n "startMonitor|reconcile\(|runRepository.save|startAttempt|jobService.submit|bindJob|
  │ … +4 lines
  └ services/orchestrator/src/main/java/com/aidevos/orchestrator/job/JobService.java:33:    public JobSubmissionResponse submit(TaskDefinition taskDefinition) {
    services/orchestrator/src/main/java/com/aidevos/orchestrator/job/JobService.java:35:        jobStore.save(job);
    … +100 lines (ctrl + t to view transcript)
    aidevos.persistence.postgresql.username=${AI_DEV_OS_POSTGRES_USER:ai_dev_os}
    aidevos.persistence.postgresql.password=${AI_DEV_OS_POSTGRES_PASSWORD:}

──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

• Phase 7 基线已确认：

  - 分支：main
  - HEAD：b41428c feat: complete phase 7 observability platform baseline
  - 工作树：干净
  - README 与会话记录均明确标记 Phase 7 已完成
  - 本次仅进行了只读检查，没有修改文件、运行服务或写入数据库

  ## 1. Job / Execution / PlanRun 当前恢复缺口

  ### Job

  当前 Job 已有 PostgreSQL 快照持久化，但还不是可靠任务队列。

  主要缺口：

  - Job 先持久化，再放入进程内 ArrayBlockingQueue。两步不原子；进程在两步之间退出会留下永远不执行的 QUEUED Job。
  - 内存队列重启即丢失，启动时没有扫描 QUEUED Job 并重新调度。
  - RUNNING 只有状态和时间，没有 worker owner、lease、heartbeat、attempt、fencing token。重启后无法判断该 Job 是否仍被其他实例执行。
  - Worker 停止时直接 shutdownNow()，没有可靠地释放执行所有权或记录中断。
  - 多实例部署时，每个实例都可能操作同一个持久化 Job；当前 Repository 没有原子 claim/CAS 能力。
  - 审批恢复仍然是“状态改为 QUEUED → 放入内存队列 → 保存”的组合操作，存在崩溃窗口和并发重复恢复窗口。
  - 队列满时会删除刚创建的 Job。在生产持久队列模型下，更适合保留 Job 并施加 admission control，而不是创建后删除。
  - 默认配置仍为 in-memory；如果生产环境遗漏环境变量，所有状态会在重启后丢失。

  依据：services/orchestrator/src/main/java/com/aidevos/orchestrator/job/JobService.java:33、services/orchestrator/src/main/java/com/aidevos/orchestrator/job/JobWorker.java:28、services/orchestrator/src/main/java/com/aidevos/orchestrator/orchestrator/persistence/
  postgresql/PostgresStateRepositories.java:15。

  ### Execution

  Execution 当前更接近“完成后写结果”，没有可恢复的执行生命周期。

  主要缺口：

  - 每次执行现场生成新 executionId，没有基于 Job attempt 的稳定执行身份。
  - ExecutionRecord 只在 Executor 返回后保存。进程在执行中退出时，数据库中可能完全没有对应 ExecutionRecord。
  - ExecutionRecord save → 审计事件 → Job terminal save 分属独立操作，任一步崩溃都可能留下不一致状态。
  - Worker 最外层异常可能产生 FAILED Job，但没有 ExecutionRecord。
  - 没有 STARTING/RUNNING/ABANDONED/RECOVERING 等持久生命周期，也没有 attempt number、worker、heartbeat、failure classification。
  - 外部 Executor 是否安全重放没有统一协议。命令、Codex、MCP 或 Browser 可能已经产生副作用，但 Job 尚未写入成功。
  - 当前只能实现 at-least-once 恢复，不能承诺 exactly-once 外部副作用。需要 Executor 幂等键或人工裁决机制。

  依据：services/orchestrator/src/main/java/com/aidevos/orchestrator/execution/ExecutionEngine.java:45、services/orchestrator/src/main/java/com/aidevos/orchestrator/execution/ExecutionRecordManager.java:48。

  ### PlanRun / StepRun / Attempt

  PlanRun 快照能够在重启后被 monitor 读取，但调度动作没有事务化，不能安全恢复。

  关键窗口：

  1. StepAttempt.startAttempt
  2. jobService.submit
  3. attempt.bindJob
  4. runRepository.save

  在这些步骤之间崩溃可能产生：

  - Attempt 已开始但没有 Job；
  - Job 已创建，但 Attempt 尚未绑定，形成 orphan Job；
  - 已绑定但 PlanRun 快照未保存；
  - 重启后 scheduler 再次提交同一步骤；
  - 多实例 monitor 同时 reconcile 同一个 PlanRun。

  另外：

  - PlanRun create → approval consume 不是同一事务。补偿删除只能处理同步异常，不能处理进程崩溃。
  - PlanScheduler 启动后每 25ms 遍历所有非终态 Run，没有 scheduler ownership、lease 或版本 CAS。
  - 已定义 RetryPolicy，但当前调度失败路径基本直接失败或请求 Replan，尚未形成持久化 retry/backoff 调度。
  - job == null 会直接使 PlanRun 失败，不区分“提交事务未完成”“数据损坏”“可恢复 orphan binding”。
  - 状态快照采用整份 JSON 文档覆盖，多个实例并发保存会发生 lost update。

  依据：services/orchestrator/src/main/java/com/aidevos/orchestrator/plan/schedule/PlanScheduler.java:122、services/orchestrator/src/main/java/com/aidevos/orchestrator/plan/schedule/PlanScheduler.java:251。

  ## 2. Worker Lease 设计

  建议 PostgreSQL 模式改为“数据库是队列真相源”，内存队列只作为本地唤醒优化。

  ### Job 控制字段

  建议为 Job 增加可索引的结构化控制字段，而不是只放在 JSON payload 中：

  - status
  - available_at
  - priority
  - attempt_no
  - max_attempts
  - lease_owner
  - lease_token，单调递增 fencing token
  - lease_expires_at
  - heartbeat_at
  - version
  - last_failure_code
  - recovery_count
  - created_at/started_at/completed_at

  业务快照仍可保留 JSONB，但 claim 所需字段必须成为数据库列。

  ### Claim 协议

  Worker 在短事务中：

  1. 查询 QUEUED 且 available_at <= database_now() 的 Job，或 lease 已过期的可恢复 Job。
  2. 使用 FOR UPDATE SKIP LOCKED 锁定候选。
  3. 原子更新为 RUNNING。
  4. 写入 owner、过期时间，递增 lease_token 和 attempt。
  5. 提交事务后再调用外部 Executor。

  所有完成、失败、续租操作必须携带：

  job_id + lease_owner + lease_token

  更新影响行数为 0 即说明 lease 已失效，旧 Worker 不得再写终态。这是防止“暂停过久的旧实例复活后覆盖新实例”的 fencing 条件。

  ### Heartbeat

  - Lease 时长建议可配置，初始可取 30–60 秒。
  - Heartbeat 间隔小于 lease 的三分之一。
  - 时间判断使用 PostgreSQL CURRENT_TIMESTAMP，避免节点时钟漂移。
  - Executor 阻塞时 heartbeat 必须由独立调度器维护。
  - 连续续租失败后停止接受新 Job；当前执行进入 lease-risk 状态，并尽可能取消外部调用。

  ### 状态语义

  建议最小状态集合：

  QUEUED
  RUNNING
  WAITING_APPROVAL
  RETRY_WAIT
  SUCCESS
  FAILED
  CANCELLED
  RECOVERY_REQUIRED

  不建议 lease 到期后无条件重跑：

  - 明确幂等的 Executor：重新入队。
  - 可用幂等键查询外部结果的 Executor：先 reconcile，再决定。
  - 不可判断副作用的 Executor：进入 RECOVERY_REQUIRED，等待人工或专用恢复器裁决。

  ## 3. Restart Recovery 方案

  ### 启动顺序

  1. 完成数据库迁移。
  2. 为实例生成稳定的 workerInstanceId。
  3. 启动 Outbox relay。
  4. 扫描并分类遗留 Job/Execution。
  5. 恢复 PlanRun reconciliation。
  6. 最后开放 readiness 和新任务接收。

  ### Job Recovery

  启动和周期性 reaper 都执行：

  - QUEUED：保持可 claim，无需搬进内存队列。
  - RUNNING + lease 未过期：视为其他实例仍持有，不处理。
  - RUNNING + lease 已过期：
      - 幂等任务转为 RETRY_WAIT/QUEUED；
      - 不确定副作用任务转为 RECOVERY_REQUIRED；
      - 超过最大恢复次数转为 FAILED。

  - WAITING_APPROVAL：保持等待，不自动重跑；若审批已批准但 Job 尚未恢复，则通过事务化 reconcile 转为 QUEUED。
  - terminal 状态：只做一致性核验。

  ### Execution Recovery

  - Executor 调用前创建持久化 ExecutionAttempt，状态设为 RUNNING。
  - executionId 在一次 attempt 内稳定，重试创建新的 attempt，并保留 parent/previous attempt 关联。
  - Job terminal、ExecutionAttempt terminal、ExecutionRecord 和对应 Outbox enqueue 在一个事务中完成。
  - 启动时将无有效 Job lease 的 RUNNING ExecutionAttempt 标为 ABANDONED，再按 Executor recovery policy 处理。
  - 向外部系统传递 jobId/attemptId/executionId 作为幂等键。

  ### PlanRun Recovery

  - PlanRun 同样需要 coordinator lease，或基于 version 的乐观锁/CAS；同一时刻只允许一个 scheduler 推进一个 Run。
  - Step 的 Job ID 使用确定性键，例如由 attemptId 推导，或数据库建立 UNIQUE(attempt_id)。
  - “创建 Attempt + 创建 Job + 绑定 Job + 保存 PlanRun + Outbox”必须同事务提交。
  - 重启发现 Attempt 无 Job 时，使用确定性键补建；发现 orphan Job 时按 attempt_id 重新绑定，不能直接创建第二个 Job。
  - PlanRun create + approval consume放入同一事务，并使用数据库唯一约束保证一个 approval 只能启动一个 Run。
  - RetryPolicy 持久化为 available_at 和 attempt 状态，重启后继续 backoff，而不是重置计时。

  总体交付语义应明确为：内部状态 exactly-once transition，外部执行 at-least-once；最终副作用一致性依赖 Executor 幂等能力。

  ## 4. Outbox Transaction 一致性优化

  当前实现中，业务 Repository 保存与 persistOutbox() 使用不同数据库连接，因此不是同一 JDBC 事务；随后同步 drainOutbox() 又会把审计发布故障传播给已经提交业务状态的调用方。

  依据：services/orchestrator/src/main/java/com/aidevos/orchestrator/audit/PostgresAuditRepository.java:32、services/orchestrator/src/main/resources/db/migration/V4__audit_outbox.sql:1。

  建议方案：

  - 引入 Spring JDBC transaction boundary，例如 TransactionTemplate 或 @Transactional application service。
  - Repository 使用事务绑定的 Connection/DataSource，禁止每个保存操作自行开启独立连接。
  - 在同一事务内完成：
      - 聚合状态更新；
      - ExecutionRecord 更新；
      - Outbox enqueue；
      - 必要的唯一约束/CAS。

  - AuditService 在事务内只负责构造并插入 Outbox，不同步发布。
  - 独立 Outbox relay 周期性批量 claim：
      - FOR UPDATE SKIP LOCKED
      - 成功后标记 published_at
      - 失败记录本行 attempts、last_error、next_attempt_at
      - 指数退避和最大重试/死信策略

  - 不应像当前实现一样在一次失败时更新全部 pending 行。
  - 保留 idempotency_key 唯一约束；消费者继续按该键幂等。
  - 增加 backlog、oldest pending age、retry count、dead-letter count 指标。
  - 进程崩溃后 relay 自动继续处理，不依赖 Audit 查询或下一次 append 来触发 drain。

  需要特别说明：当前 audit_events 与 outbox 位于同一 PostgreSQL。短期可把 relay 理解为可靠的“审计物化器”；未来若发布到 Kafka/NATS，仍沿用相同 Outbox 协议。

  ## 5. Phase 8 分阶段实施计划

  ### Phase 8-A：可靠性模型与数据库基础

  - 定义 Job、ExecutionAttempt、PlanRun 的状态机与恢复策略。
  - 增加结构化 Job 控制表/字段、版本、lease、attempt、唯一约束和索引。
  - 增加 Repository 原子 claim、renew、complete、release、CAS 接口。
  - 保持 in-memory adapter，用于开发与契约测试。
  - 增加迁移兼容和旧 Job 分类策略。

  验收：Repository contract、并发 claim、fencing、迁移测试通过。

  ### Phase 8-B：Worker Lease 与持久队列

  - PostgreSQL Worker 从数据库 claim Job。
  - 实现 heartbeat、lease reaper、graceful shutdown。
  - 本地队列降级为唤醒/吞吐优化，不再是真相源。
  - 加入 admission control、backoff、最大恢复次数。
  - 提供 worker/lease 健康指标。

  验收：双实例只能执行一次 claim；kill -9 后 lease 到期可恢复；旧 worker 无法覆盖新 worker。

  ### Phase 8-C：Execution 与 Restart Recovery

  - 执行前持久化 ExecutionAttempt。
  - 引入 Executor recovery policy 和 idempotency contract。
  - 实现 startup recovery、周期性 stale execution recovery。
  - 审批状态与 Job 恢复事务化。
  - 对不可安全重放任务引入 RECOVERY_REQUIRED。

  验收：分别在 claim 后、Executor 中、结果返回后、终态保存前模拟崩溃，状态均可解释且可恢复。

  ### Phase 8-D：PlanRun 可靠调度

  - 增加 PlanRun coordinator lease 或版本 CAS。
  - 原子化 approval consume/run create。
  - 原子化 attempt/job create/bind。
  - 使用 UNIQUE(attempt_id) 或确定性 Job ID 去重。
  - 落实持久化 retry/backoff。
  - 实现 orphan Job 和 missing binding 修复。

  验收：多 scheduler 实例不会重复推进；各事务断点重启后不会重复创建 Job。

  ### Phase 8-E：Transactional Outbox

  - 建立统一事务边界。
  - 聚合更新与 Outbox enqueue 同事务。
  - 拆出后台 relay、退避、死信和监控。
  - 调整审计 API，使查询不承担 drain 副作用。
  - 补充事务回滚和 crash-window 集成测试。

  验收：业务提交必有 Outbox；业务回滚无 Outbox；发布失败不回滚已提交业务；恢复后最终可见且不重复。

  ### Phase 8-F：生产验证与运维门禁

  - Testcontainers PostgreSQL 并发/故障注入测试。
  - 双实例 Docker 验证、滚动重启和 kill -9 演练。
  - Outbox backlog、lease expiry、recovery-required 告警。
  - readiness 在迁移和 recovery scan 完成前保持失败。
  - 更新 Runbook、状态说明、升级与回滚文档。
  - 全量 Maven、前端及 Phase 7 回归测试。

  ## 建议实施顺序

  建议按 8-A → 8-B → 8-C → 8-D → 8-E → 8-F 执行。8-A 是后续所有恢复能力的基础；Outbox 的公共事务抽象可以在 8-A 先定义，但完整 relay 放在 8-E，以控制单阶段改动范围。

  潜在影响主要集中在数据库迁移、Job 状态/API 展示、Worker 调度方式、审批恢复语义和部署配置。不会要求大范围重构 Planner 或具体 Executor，但 Executor 需要声明其幂等与恢复能力。

  当前停在设计确认点，未执行任何修改。

─ Worked for 2m 39s ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────


› 确认进入 Phase 8-A。

  先不要修改代码。

  请先输出 Phase 8-A 实施设计：

  1. 数据库迁移设计
     - 新增表/字段
     - 索引
     - 唯一约束
     - 与现有表兼容方案

  2. 状态模型设计
     - Job 状态变化
     - ExecutionAttempt 状态
     - Recovery 状态

  3. Repository SPI 变化
     - 新增接口
     - InMemory/PostgreSQL 实现影响

  4. 事务边界设计
     - 哪些操作需要原子事务
     - 哪些保持现有逻辑

  5. 修改文件清单

  6. 测试方案

  等待确认后再实施。
