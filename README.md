# AI Dev OS

当前版本： v1.2.2

## 0. 2026-08-05 Phase 8 生产可靠性完成

Phase 8（Production Reliability）已完整交付，含 8-F 生产验证与运维门禁。

- 可靠性持久化：V1～V7 版本化迁移、`jobs`/`execution_attempts` 结构化控制列、
  PlanRun version CAS、audit outbox relay 控制列与索引。
- Worker Lease：数据库原子 claim（`FOR UPDATE SKIP LOCKED`）、heartbeat、
  fencing token、lease reaper；kill -9 后过期 lease 进入 `RECOVERY_REQUIRED`。
- 可靠调度：PlanRun coordinator lease、确定性 Job ID 幂等提交，多 scheduler
  实例不会重复推进。
- Transactional Outbox：业务状态与 outbox 入队同 JDBC 事务，后台 relay 退避
  重试与死信，发布失败不回滚已提交业务。
- 运维门禁：`GET /api/health` 存活探针与 `GET /api/health/readiness` 就绪探针
  （迁移未完成前保持 503）；双实例并发、迁移新库/旧库升级、Worker/Scheduler/
  Outbox 故障恢复均通过 Testcontainers PostgreSQL 验证。
- 新增 `docs/operation/runbook.md` 运维手册（启动、健康检查、故障恢复、升级
  回滚、告警 SQL）。
- 全量回归：413 项测试，0 failure、0 error、1 skipped。

## 0. 2026-08-03 Phase 7 完整基线

Phase 7 Review、修复与全量验证已完成，暂不进入 Phase 8。

- PostgreSQL Persistence：Repository 双模式、V1～V4 版本化迁移、数据库级 PlanVersion freeze、常用状态查询下推与恢复能力已形成基线。
- Audit Core：补齐 Plan、Replan、Step、Agent selection、Tool/MCP、Execution 与 Artifact 事件；修正幂等键，PostgreSQL 使用 durable outbox 入队、发布与失败重试。
- Timeline API / Audit Console：查询、过滤、分页和计数下推 PostgreSQL；API 和前端同时返回并展示 `totalCount` / `hasMore`。
- 生产入口：新增 `POST /api/planning` 与 `POST/GET /api/plan-runs`，补齐 User Request 到 Hermes、审批后到 PlanScheduler 的生产链入口。
- 端到端链已通过：`User Request → Hermes Plan → Plan Approval → PlanScheduler → Job → Agent → Tool/MCP → Execution → Audit Event → Timeline`。
- 后端全量测试：264 项，0 failure、0 error，1 个需外部 filesystem MCP 环境的可选验收项默认跳过；Testcontainers PostgreSQL 实际运行通过。
- InMemory 与 PostgreSQL 两种模式均完成真实启动、只读 Audit API 和优雅关闭验证；前端 TypeScript 检查与 production build 通过。

当前边界：durable audit outbox 能防止审计发布失败导致事件丢失并在后续访问时重试；业务 Repository 状态写入与 outbox 入队尚未共享同一个 JDBC 事务。前端 production build 仍有约 575.62 kB 的 chunk size 警告，均作为后续治理项，不在本次 Phase 7 基线中扩展处理。
