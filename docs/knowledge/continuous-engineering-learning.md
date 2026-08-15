# Continuous Engineering Learning

## Purpose

AI Dev OS 的工程知识必须从真实失败和可追踪证据中产生，而不是从模型猜测或一次性总结中产生。本机制定义未来如何把问题转化为可审查的 EPR、Lesson 和 Guardrail。

## Standard loop

```text
Task / Plan / Execution
  → Failure / Unexpected Behavior
  → Problem Candidate
  → Investigation
  → Root Cause Confirmed
  → Resolution
  → Regression Verification
  → Engineering Problem Record
  → Engineering Lesson
  → Guardrail / Test / Planning Rule
```

## Problem Candidate

可生成 Candidate 的信号包括：

- Task、Execution 或 Analysis Projection FAILED
- schema validation failure
- artifact contract mismatch
- agent routing mismatch
- approval/permission anomaly
- persistence/migration failure
- retry 无法恢复
- E2E lineage/state 不一致
- 人工明确指出系统行为错误
- 修复后重新执行成功并证明此前存在真实问题

Candidate 不是正式知识。用户输入错误、普通网络失败、预期业务拒绝和未确认猜测不得自动升级为 EPR。

## Problem lifecycle

```text
OPEN
  → INVESTIGATING
  → ROOT_CAUSE_CONFIRMED
  → RESOLVED
  → VERIFIED
  → KNOWLEDGE_CAPTURED
```

另有终态：`NOT_A_PROBLEM`、`DUPLICATE`。此生命周期独立于 Task、Backlog、Approval、Execution 状态机。

### Evidence requirement

Root Cause Confirmed 至少要能引用一个真实、可回溯的证据；可用证据包括：

- taskId、planRunId、stepRunId、jobId、executionRecordId
- analysisId、approvalId、artifact
- source file、timeline event、commit、test result

AI 可以收集证据、起草 Root Cause 和 EPR，但不能仅凭总结自动提升到 `ROOT_CAUSE_CONFIRMED` 或 `VERIFIED`。

## Human review boundary

未来系统可以自动：

- 创建 Problem Candidate
- 收集 Evidence
- 起草 Root Cause / EPR
- 建议 Lesson 和 Guardrail

必须由人工确认后才能：

- `ROOT_CAUSE_CONFIRMED`
- `VERIFIED`
- `ENFORCED`

## Knowledge lifecycle

EPR 和 Lesson 长期状态使用：

- `ACTIVE`
- `SUPERSEDED`
- `OBSOLETE`

旧记录可以保留历史价值，但 SUPERSEDED/OBSOLETE 不得继续作为当前强制 Guardrail。

## Contextual lesson use

未来 Agent 不应把整个 `docs/knowledge/` 塞入 prompt。建议流程：

```text
Task Context
  → Topic / Capability Classification
  → Relevant Lesson Retrieval
  → Planning / Implementation Guardrails
```

候选 topic 包括 identity、migration、approval、artifact、retry、workspace、routing、browser runtime。检索结果必须保留 Lesson 状态和 Evidence 来源。

## Guardrail promotion

Lesson 可按风险和验证程度建议提升为：

- Hermes Planning Guardrail
- Coder Implementation Guardrail
- Reviewer Check
- CI/Test Gate
- Runtime Validation
- Migration Validation

写入文档本身不等于系统已经自动防止回归。只有实际接入 Planner、CI、Runtime 或 Migration gate 后，状态才能标记 `ENFORCED`。

## Roadmap relationship

仓库已有 [Knowledge Capture / Lessons Review V1](../roadmap/README.md#knowledge-capture--lessons-review-v1)，其安全边界是 Analyzer 只能产生 Candidate，管理员 Accept/Edit 后才能写入正式 Knowledge Base。

KNOWLEDGE-001 是该路线的 Foundation，不创建重复 Backlog 或 Roadmap 项。后续建议：

1. **KNOWLEDGE-002 — Problem Candidate Capture**：从失败、异常和 E2E 证据生成候选，保留人工审核边界。
2. **KNOWLEDGE-003 — Engineering Lesson Extraction**：从已 VERIFIED EPR 提炼 Lesson 草案并合并重复规则。
3. **KNOWLEDGE-004 — Contextual Lesson Retrieval**：按 Task context/topic 检索相关 Lesson，避免全量注入 prompt。
4. **KNOWLEDGE-005 — Guardrail Promotion & Enforcement**：把已批准 Lesson 接入 Planning、CI、Runtime、Migration gates。

本文件只设计 002–005，不实施它们。

## Future directions (not implemented here)

### Isolated Execution Workspace

Task 使用独立 worktree/sandbox/container；sandbox 内提高自主权限，sandbox 外严格限制；完成后人工 Review/Promotion，避免污染真实 Workspace 和其他 Task。

### Unified Multi-Approval Gate

统一支持 workspace write、dangerous command、tool permission、external access、deployment 等多次独立 Approval；一次审批不授权未知未来动作。

### Executor Permission Policy

Codex permission request 尽量纳入 AI Dev OS Policy；低风险 deterministic operation 在隔离 sandbox 内可自动允许，高风险/外部副作用必须人工 Approval。

### Deterministic Tools vs LLM

read、git status/diff、build/test、localhost GET 等确定性操作优先走 Tool/Policy，不无意义消耗 LLM token。

### Execution Recovery / Resumability

继续建设 lease、heartbeat、timeout、restart recovery 和 multi-instance ownership。

### Multi-task concurrency

不同 isolated workspace 可并发；同一 workspace 的 READ_WRITE 必须有冲突控制。

### Visual Readability & Hierarchy

后续统一处理白底、灰字、status/action hierarchy、Approval / Action Required 可见性和视觉 tokens。
