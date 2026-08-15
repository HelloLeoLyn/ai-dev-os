# EPR-005 — Expected Artifact / Executor Artifact contract mismatch

- **Problem ID:** EPR-005
- **Title:** Expected Artifact / Executor Artifact contract mismatch
- **Status:** VERIFIED
- **Severity:** HIGH
- **Category:** Artifact Contract
- **Detected At:** READ_WRITE implementation E2E

## Context

Plan 的 expectedArtifacts 由 Planner 声明，CodexExecutor 产生实际 artifacts，Artifact Gate 负责确定性校验。

## Symptoms

Plan 要求 `result/application-json`，Codex 实际产生 `changes.patch / git-diff / text/plain`，Execution 失败并提示 expected artifact requirements 未满足。

## Trigger / Reproduction

使用与 CodexExecutor 不一致的 artifact contract 创建 coder step。

## Impact

实际代码变更已产生或可产生，但 Task 被错误判定为 artifact contract failure。

## Root Cause

Planner 与 Executor 各自定义 artifact 名称和媒体类型，没有共享同一事实源。

## Contributing Factors

- mock/planner 路径掩盖了真实 Codex contract。
- 测试只验证 gate 失败，没有验证 expected/actual 成功匹配。

## Why Existing Tests Missed It

既有测试没有使用真实 coder contract 的 `changes.patch/git-diff/text/plain`。

## Resolution

READ_WRITE coder step 统一使用：`changes.patch`, `git-diff`, `text/plain`, required=true, minimumCount=1。

## Files / Components Affected

- `HermesPlanner`
- Plan step artifact contract
- Artifact Gate / Codex executor tests

## Verification Evidence

- expected artifact 与 Codex git-diff changes.patch 匹配。
- StepTaskFactory 保留 execution mode/workspacePath。
- artifact gate regression 通过。

## Prevention

### Engineering Guardrails

Planner 只能引用 executor registry 中的 artifact contract，不得复制第二套定义。

### Required Regression Tests

- expected vs actual matched。
- missing artifact rejected。
- mediaType/type/name/minimumCount 全量校验。

### Detection Signals

ExecutionRecord failure 且 expected/actual artifact name/type 不一致。

### Do Not Fix By

- 删除 expectedArtifacts。
- 让 mock 伪造 artifact。
- 绕过 artifact gate。

## Generalized Lesson

Planner and Executor must share executable contracts。

## Related Problems

EPR-004、EPR-007。

## Related Commits / WorkItems

Planner Routing + Artifact Contract 最小修复。

## Future Follow-ups

Execution 页面展示 Expected vs Actual，但不改变 gate 语义。
