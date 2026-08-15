# EPR-002 — Codex strict JSON Schema required/properties mismatch

- **Problem ID:** EPR-002
- **Title:** Codex strict JSON Schema required/properties mismatch
- **Status:** VERIFIED
- **Severity:** HIGH
- **Category:** Structured Output
- **Detected At:** project-analysis Codex invocation

## Context

project-analysis 使用 Codex structured output 生成 Findings、Recommendations 和 EvidenceRef。

## Symptoms

Codex API 返回 `invalid_request_error` / `invalid_json_schema`，提示 object 的 properties 存在字段但 required 缺少 `label`，随后还可能暴露其他嵌套字段问题。

## Trigger / Reproduction

将带有 `EvidenceRef.label` 等字段但 required 不完整的 schema 发送给 strict response format。

## Impact

分析 Task 无法开始，错误发生在执行前而非业务分析阶段。

## Root Cause

strict provider contract 要求每个 object 的 `required` 完整覆盖 `properties`。schema 只在顶层或单个节点修复是不充分的。

## Contributing Factors

- 可选字段按普通 JSON Schema 习惯从 required 删除。
- 没有递归校验 array items 中的 object。

## Why Existing Tests Missed It

测试验证了 schema 可序列化，却没有递归比较每个 object 的 required/property key 集合。

## Resolution

补齐 project-analysis schema 的所有 object required；逻辑可选字段使用 nullable 类型表达；增加递归 contract test。

## Files / Components Affected

- `CodexOutputSchemaProvider`
- `CodexCommandBuilder`
- schema regression tests

## Verification Evidence

- `CodexOutputSchemaProviderTest` 递归检查通过。
- `CodexCommandBuilderTest` 通过。
- `mvn test` 通过。

## Prevention

### Engineering Guardrails

`required key set == properties key set` 必须是 strict schema 的自动门禁。

### Required Regression Tests

- 递归 object 检查。
- array items 嵌套 object 检查。
- 非 project-analysis schema compatibility test。

### Detection Signals

Codex 返回 `invalid_json_schema` / HTTP 400。

### Do Not Fix By

- 只删除缺失字段。
- 放宽 strict response format。
- 把 schema 错误转成普通 Task failure。

## Generalized Lesson

Strict structured-output schema must satisfy the provider’s complete required contract。

## Related Problems

EPR-007（structured evidence contract）。

## Related Commits / WorkItems

V04-WORK-005A project-analysis structured output 修复。

## Future Follow-ups

在 schema provider 层统一提供 strict contract validator。
