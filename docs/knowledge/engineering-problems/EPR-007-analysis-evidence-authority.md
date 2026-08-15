# EPR-007 — project-analysis EvidenceRef authority mismatch

- **Problem ID:** EPR-007
- **Title:** project-analysis EvidenceRef authority mismatch
- **Status:** VERIFIED
- **Severity:** HIGH
- **Category:** Structured Output
- **Detected At:** project-analysis retry E2E

## Context

Analysis Projection 必须验证 EvidenceRef 是否属于 source Execution、source Artifact 或 workspace source file。

## Symptoms

Codex 输出 `type=EXECUTION_RECORD, ref="git diff --check"`，Projection 返回 `EXTRACTION_FAILED: evidence crosses execution boundary`。Task 本身仍 SUCCESS。

## Trigger / Reproduction

模型把命令名称当作 ExecutionRecord identity，而不是引用实际 artifact 或 source file。

## Impact

合法完成的 source Task 无法生成 AnalysisInsight；retry 只是重复读取同一非法 `analysis-result.json`，因此稳定失败。

## Root Cause

模型不知道随后持久化生成的 ExecutionRecord UUID；允许它直接生成 EXECUTION_RECORD ref 是不可满足的 evidence contract。

## Contributing Factors

- 自然语言命令与持久化资源 ID 没有区分。
- schema 没有把 authority 范围收紧到可被模型可靠引用的资源。

## Why Existing Tests Missed It

测试覆盖了合法/非法 validator 分支，但没有把真实 Codex structured output 与 persisted ExecutionRecord 生命周期连起来。

## Resolution

project-analysis schema 只允许 SOURCE_FILE / ARTIFACT；validator 保持严格；prompt/schema 提供稳定 artifact names 和 workspace-relative path contract；错误信息细化。

## Files / Components Affected

- `CodexOutputSchemaProvider`
- `AnalysisPayloadValidator`
- Analysis Projection tests

## Verification Evidence

- 命令名称作为 EXECUTION_RECORD ref 被拒绝。
- 真实 source execution ID 被接受。
- artifact/source file boundary tests 通过。
- 非法历史 payload retry 不生成第二份 Insight，保持 FAILED。

## Prevention

### Engineering Guardrails

Evidence ref 必须映射到确定 authority；不能接受命令字符串冒充资源 ID。

### Required Regression Tests

- EXECUTION_RECORD exact ID。
- ARTIFACT existing/missing。
- SOURCE_FILE workspace escape。
- strict schema allowed evidence types。

### Detection Signals

`EXTRACTION_FAILED`、`evidence crosses execution boundary` 或 retry deterministic failure。

### Do Not Fix By

- 自动把任意 ref 替换为当前 ExecutionRecord ID。
- 跳过 Evidence validation。
- 修改历史 artifact 伪造合法证据。

## Generalized Lesson

Structured Evidence must have deterministic authority。

## Related Problems

EPR-002、EPR-005。

## Related Commits / WorkItems

Analysis Evidence Contract 最小修复。

## Future Follow-ups

评估受控 `CURRENT_EXECUTION` sentinel；当前不实现。
