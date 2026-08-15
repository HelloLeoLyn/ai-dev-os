# EPR-004 — READ_WRITE Task routed to planner/mock

- **Problem ID:** EPR-004
- **Title:** READ_WRITE Task routed to planner/mock
- **Status:** VERIFIED
- **Severity:** CRITICAL
- **Category:** Executor / Routing
- **Detected At:** `Implement and test dispatch-status migration` E2E

## Context

普通 READ_WRITE 软件工程 Task 应由具备 coding + git capability 的 coder/Codex 路径执行，并继续经过 Plan、Approval、Execution。

## Symptoms

Task 已 RUNNING，Plan 已 CONSUMED，但 Agent 是 planner，ExecutionRecord 失败，Codex 没有实际执行。

## Trigger / Reproduction

HermesPlanner 使用 `snapshot.agents().findFirst()`；registry 顺序恰好是 planner(mock)、analyst、coder。

## Impact

实施 Task 进入错误 executor，产生错误结果并触发 expected artifact gate 失败。

## Root Cause

Agent routing 依赖 registry 顺序而不是 Task intent 与 capabilities。

## Contributing Factors

- planner/mock 与 coding executor 都能被统一 Agent 列表发现。
- Task metadata 中 requested capability 未完整传入 PlanningRequest。

## Why Existing Tests Missed It

测试依赖标准 registry 顺序，未故意把 planner 放在第一位，也未验证 READ_WRITE 必须 coding + git。

## Resolution

READ_WRITE implementation step 选择 enabled 且具备 coding + git capability 的 coder；READ_ONLY/project-analysis 专用逻辑保持不变。

## Files / Components Affected

- `HermesPlanner`
- `StepTaskFactory`
- Agent routing tests

## Verification Evidence

- planner-first registry regression test 选择 coder。
- required capabilities 为 coding + git。
- Backlog → Task → Plan integration test 保留 Approval。
- `mvn test` 通过。

## Prevention

### Engineering Guardrails

- READ_WRITE implementation 不得选择 planner/mock。
- routing 必须检查 intent/capabilities，不得使用 `findFirst()`。

### Required Regression Tests

- planner-first registry。
- coder capability contract。
- READ_ONLY 与 project-analysis non-regression。

### Detection Signals

READ_WRITE step 的 assigned agent 不具备 coding + git，或 executor 类型为 mock/planner。

### Do Not Fix By

- 调整 registry 排序碰运气。
- 把 planner 改造成 coding executor。
- 绕过 Approval 或 artifact gate。

## Generalized Lesson

Agent routing must be capability-driven。

## Related Problems

EPR-005。

## Related Commits / WorkItems

Planner Routing + Artifact Contract 最小修复。

## Future Follow-ups

将 task intent、requestedCapabilities 作为结构化 PlanningRequest 字段，而不是从描述推断。
