# AI Dev OS

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
