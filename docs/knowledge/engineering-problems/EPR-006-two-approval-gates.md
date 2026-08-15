# EPR-006 — Plan Approval 与 Coding Approval 两层审批不可见

- **Problem ID:** EPR-006
- **Title:** Plan Approval 与 Coding Approval 两层审批不可见
- **Status:** VERIFIED
- **Severity:** CRITICAL
- **Category:** Approval / Permission
- **Detected At:** READ_WRITE Approval → Execution E2E

## Context

Plan Approval 授权 PlanRun 启动；Coding Approval 授权 Codex workspace-write。两者是不同 authority boundary。

## Symptoms

Plan Approval 已 `CONSUMED`，Task 为 RUNNING，但 ExecutionRecord 为 `WAITING_APPROVAL`，用户误以为执行卡死。

## Trigger / Reproduction

READ_WRITE Job 首次触发 Coding Approval gate 后，前端只展示含糊的 Approval 状态，没有显示 Coding Approval action。

## Impact

用户无法知道需要批准 workspace write；Job 没有 resume，Codex 不会继续执行。

## Root Cause

两个审批层级在 UI projection 中被合并或隐藏，且 next action 没有基于 active approval gate 生成。

## Contributing Factors

- Plan approval consumed 与 coding approval pending 可同时成立。
- Execution correlation（Job/attempt/record）不完整。

## Why Existing Tests Missed It

测试覆盖了单层 Plan Approval，却没有跨组件验证 Coding Approval pending → approve → same Job resume。

## Resolution

Execution UX 明确区分 Plan Approval 与 Coding Approval，显示 active gate、Action Required、Job 和 resume 状态；继续使用现有 approve/resume API。

## Files / Components Affected

- Task Workspace / Execution view
- Coding Approval API
- Job resume chain

## Verification Evidence

- 跨组件 READ_WRITE integration test 验证 Approval → WAITING_APPROVAL → approve → same Job requeue。
- 第一条 WAITING_APPROVAL attempt 保留，后续 attempt 独立显示。

## Prevention

### Engineering Guardrails

每种 authorization boundary 必须有独立 type/status/active gate/next action。

### Required Regression Tests

- Plan Approval CONSUMED + Coding Approval PENDING 同时表达。
- approve 后 same Job resume。
- GET/render 不产生 mutation。

### Detection Signals

Task RUNNING + Execution WAITING_APPROVAL + active approval 未出现在 UI。

### Do Not Fix By

- 隐藏 WAITING_APPROVAL。
- 自动批准 Coding Approval。
- 把 Plan Approval 当成 workspace write 授权。

## Generalized Lesson

Approval grants a specific authority, not blanket task permission。

## Related Problems

EPR-004、EPR-005。

## Related Commits / WorkItems

V04-WORK-008C2 Approval & Execution Transparency Closure。

## Future Follow-ups

Unified Multi-Approval Gate 设计；不在本记录中扩大审批状态机。
