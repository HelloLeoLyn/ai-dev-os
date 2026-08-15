# Engineering Problem Records Index

本目录记录已经通过可追踪证据确认的工程问题。EPR 不是 Task、Backlog 或 Incident 的替代物；它是经过调查、修复和验证后可长期复用的知识记录。

## Records

| ID | Title | Category | Severity | Status | Key Lesson | Related Component |
|---|---|---|---|---|---|---|
| [EPR-001](engineering-problems/EPR-001-browser-timer-illegal-invocation.md) | Browser timer Illegal invocation | Browser Runtime | HIGH | VERIFIED | Browser host API 必须在正确 receiver/context 下调用 | Frontend TaskPollingMonitor |
| [EPR-002](engineering-problems/EPR-002-codex-schema-required-properties.md) | Codex strict JSON Schema required/properties mismatch | Structured Output | HIGH | VERIFIED | Strict schema 的 required 必须覆盖全部 properties | CodexOutputSchemaProvider |
| [EPR-003](engineering-problems/EPR-003-workspace-repository-nullability.md) | Workspace repository_url nullability mismatch | Persistence / Migration | HIGH | VERIFIED | Domain、DTO、数据库 nullable contract 必须一致 | Workspace persistence |
| [EPR-004](engineering-problems/EPR-004-read-write-planner-routing.md) | READ_WRITE Task routed to planner/mock | Executor / Routing | CRITICAL | VERIFIED | Agent routing 必须由 capability/intent 驱动 | HermesPlanner |
| [EPR-005](engineering-problems/EPR-005-artifact-contract-mismatch.md) | Expected Artifact / Executor Artifact contract mismatch | Artifact Contract | HIGH | VERIFIED | Planner 与 Executor 必须共享可执行 artifact contract | Artifact gate / CodexExecutor |
| [EPR-006](engineering-problems/EPR-006-two-approval-gates.md) | Plan Approval 与 Coding Approval 两层审批不可见 | Approval / Permission | CRITICAL | VERIFIED | Approval 只授予特定 authority，不能被概括成一个状态 | PlanRun / Job / Coding Approval |
| [EPR-007](engineering-problems/EPR-007-analysis-evidence-authority.md) | project-analysis EvidenceRef authority mismatch | Structured Output | HIGH | VERIFIED | Evidence 必须引用确定 authority 的资源 | AnalysisPayloadValidator |
| [EPR-008](engineering-problems/EPR-008-recommendation-local-global-identity.md) | Recommendation local ID used as global identity | Identity | CRITICAL | VERIFIED | LLM local ID 不能直接作为持久化全局身份 | Analysis Projection / Recommendation |

## Topic index

| Topic | Records |
|---|---|
| Identity | EPR-008 |
| State / Workflow | EPR-006 |
| Approval / Permission | EPR-006 |
| Executor / Routing | EPR-004 |
| Artifact Contract | EPR-005 |
| Structured Output | EPR-002, EPR-007 |
| Persistence / Migration | EPR-003 |
| Browser Runtime | EPR-001 |
| Recovery / Retry | EPR-007, EPR-008 |

## Status semantics

- `VERIFIED`: 根因、修复和回归证据均已确认。
- `ROOT_CAUSE_CONFIRMED`: 根因已确认，修复或验证尚未完成。
- `DOCUMENTED`: 已记录但还没有足够证据升级。

EPR 的 `Status` 不会改变任何生产状态机，也不代表自动 Guardrail 已经启用。
