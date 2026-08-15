# EPR-008 — Recommendation local ID used as global identity

- **Problem ID:** EPR-008
- **Title:** Recommendation local ID used as global identity
- **Status:** VERIFIED
- **Severity:** CRITICAL
- **Category:** Identity
- **Detected At:** Recommendation → Backlog E2E

## Context

Codex 在每份 Analysis 内输出 `R-001` 等局部引用。Recommendation Decision 和 WorkItem 需要跨请求、跨 Analysis 的稳定全局身份。

## Symptoms

不同 Analysis 的 `R-001` 共享 `WORKITEM_CREATED`、Decision、Backlog stable ID 或 authoritative API 状态。

## Trigger / Reproduction

Repository、API 或前端 state map 直接把模型的 `R-001` 作为 Recommendation key。

## Impact

Recommendation lineage 跨 Analysis 串线，用户看到错误的 WorkItem 或决策状态。

## Root Cause

系统没有区分模型局部 identity 和持久化全局 identity。

## Contributing Factors

- local ID 看起来稳定且易读。
- legacy 查询存在“取最新一条”的危险语义。
- Decision 与 Backlog backlink 没有一致性检查。

## Why Existing Tests Missed It

测试只使用一个 Analysis，未构造 A/R-001 与 B/R-001 的碰撞场景。

## Resolution

服务端 Projection 基于 `analysisId + localRecommendationId` 确定性生成 global ID；Decision、API、frontend state、Backlog stable ID 全部使用 global ID；legacy 多匹配明确冲突。

## Files / Components Affected

- Analysis Projection / domain model
- Recommendation repository/service/API
- Decision repository
- V30 migration
- frontend analysis types

## Verification Evidence

- 同 Analysis retry global ID 稳定。
- A/B 相同 local ID 生成不同 global ID。
- A 的 View/Ignore/WorkItem 不影响 B。
- PostgreSQL fresh、V1–V29 upgrade、legacy collision tests 通过。

## Prevention

### Engineering Guardrails

- LLM local ID 永远不能直接作为 global persistent ID。
- local 多匹配必须 `AMBIGUOUS/CONFLICT`，禁止静默选择最新。
- Decision 必须校验 analysis/sourceTask 一致性。

### Required Regression Tests

- A/B 同 local ID isolation。
- retry stability。
- legacy unique vs ambiguous mutation。
- WorkItem stable ID isolation/idempotency。

### Detection Signals

不同 Analysis 出现相同 decision/backlog reference，或 local ID 查询返回多个结果。

### Do Not Fix By

- 加时间排序取最新。
- 让前端生成 global ID。
- 修改旧数据但无法证明 lineage。

## Generalized Lesson

LLM-generated local identifiers must never be used directly as global persistent identities。

## Related Problems

EPR-003、EPR-007。

## Related Commits / WorkItems

RECOMMENDATION-IDENTITY-001；V30 migration。

## Future Follow-ups

扩展 Finding identity audit；当前 Finding 仍被限制在 Analysis 内，无证据表明存在同类跨 Analysis 风险。
