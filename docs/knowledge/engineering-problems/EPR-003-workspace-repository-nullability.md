# EPR-003 — Workspace repository_url nullability mismatch

- **Problem ID:** EPR-003
- **Title:** Workspace repository_url nullability mismatch
- **Status:** VERIFIED
- **Severity:** HIGH
- **Category:** Persistence / Migration
- **Detected At:** Workspace create/attach E2E

## Context

Project 和 Workspace 的 `repositoryUrl` 是可选 metadata；用户可以先创建没有 repository URL 的 Project，再接入已有本地 Git Workspace。

## Symptoms

Project `repositoryUrl=null` 时，Workspace 持久化因 PostgreSQL `workspaces.repository_url NOT NULL DEFAULT ''` 约束失败，最终只显示 `Unexpected server error`。

## Trigger / Reproduction

Create Project without `repositoryUrl` → attach existing local Git Workspace → explicit SQL bind `NULL`。

## Impact

合法 Workspace 无法创建；HTTP 500 隐藏了真实数据库约束冲突。

## Root Cause

领域模型、DTO 和 projects 表允许 null，但 V13 后 workspaces 表仍要求非 null，三层 persistence contract 不一致。

## Contributing Factors

- 旧 migration 未随领域 nullable 语义演进。
- 异常处理只返回通用 server error。

## Why Existing Tests Missed It

已有测试覆盖了带 repository URL 的 Workspace，没有覆盖 null round-trip 的真实 PostgreSQL 路径。

## Resolution

新增 V28，移除 Workspace `repository_url` 的 NOT NULL/default 约束；保留 null 语义；增加 Testcontainers PostgreSQL 回归测试。

## Files / Components Affected

- `V28__nullable_workspace_repository_url.sql`
- PostgreSQL Workspace repository
- Workspace integration tests

## Verification Evidence

- Project null + local Git Workspace 场景通过。
- reload 后 `repositoryUrl` 仍为 null。
- projectId/path/branch round-trip 正确。
- `mvn test` 通过。

## Prevention

### Engineering Guardrails

Domain nullable、DTO nullable、SQL nullable 必须在 contract review 中同时核对。

### Required Regression Tests

- Testcontainers null insert/reload。
- InMemory 与 PostgreSQL 行为一致。
- fresh migration 与 upgrade migration。

### Detection Signals

PostgreSQL NOT NULL violation、以及通用 500 与持久化约束错误同时出现。

### Do Not Fix By

- 将 null 强制改成空字符串。
- 修改业务 metadata 语义。
- 重写旧 migration 或清理数据库数据。

## Generalized Lesson

Domain, DTO and persistence schema must agree on nullability and identity。

## Related Problems

EPR-008（持久化 identity contract）。

## Related Commits / WorkItems

Workspace repository URL nullable fix；V28 migration。

## Future Follow-ups

提高数据库约束异常的可观察性，但不以通用错误替代安全的 API contract。
