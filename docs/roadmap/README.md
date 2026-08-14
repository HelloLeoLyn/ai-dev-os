# AI Dev OS Roadmap

本文件是 `docs/roadmap/` 的唯一统一规划索引，负责登记规划状态与指向已有规划文档。规划项进入实施阶段后，再建立对应的详细实施文档。

## 现有规划文档

- [AI Dev OS v1.1 Roadmap](v1.1-plan.md)
- [Phase 9-A-1 Dashboard 基础实现计划](phase9-a1-dashboard-plan.md)

## 规划项

| Name | Status | Priority | Dependency | Details |
| --- | --- | --- | --- | --- |
| Backlog / Roadmap Center V1 | PLANNED | TBD | TBD | details TBD |
| Knowledge Capture / Lessons Review V1 | PLANNED | HIGH | Backlog / Roadmap Center V1 | 见下方登记 |
| Remote Channel Gateway V1 | PLANNED | TBD | TBD | details TBD |

### Backlog / Roadmap Center V1

- Status: `PLANNED`
- Priority: `TBD`
- Dependency: `TBD`
- Details: `TBD`

仓库当前没有足够事实确认其优先级、依赖关系和设计细节。本项仅作正式规划登记。

### Knowledge Capture / Lessons Review V1

- Status: `PLANNED`
- Priority: `HIGH`
- Dependency: `Backlog / Roadmap Center V1`
- Goal: 允许管理员在 Task Detail 主动执行经验提炼，基于真实 Task Evidence 生成 Lesson Candidates，让同类工程问题尽量只发生一次。

Task Evidence 输入：

- Task
- Plan
- Execution
- Validation
- Security
- Browser
- Quality Gate
- Audit
- Timeline
- Artifact
- ChangeSet / Commit

Candidate actions：

- `CREATE_LESSON`
- `APPEND_EVIDENCE`
- `PROMOTE_MATURITY`
- `CREATE_BACKLOG`
- `NO_ACTION`

Admin Review：

- `Accept`
- `Edit`
- `Reject`

安全边界：Lesson Analyzer 只能生成 Lesson Candidate，不允许直接修改正式 Knowledge Base。只有管理员完成 `Accept` 或 `Edit` 确认后，系统才可以创建 Lesson、追加 Evidence、提升 Maturity 或创建 Backlog；`Reject` 不产生正式变更。

实施阶段：

- K1 Task Lessons Evidence
- K2 Lesson Candidate Analyzer
- K3 Admin Review
- K4 Knowledge + Backlog Integration

当前仅登记为 `PLANNED`，不实施任何功能。进入实施阶段时另建详细实施文档。

### Remote Channel Gateway V1

- Status: `PLANNED`
- Priority: `TBD`
- Dependency: `TBD`
- Details: `TBD`

仓库当前没有足够事实确认其优先级、依赖关系和设计细节。本项仅作正式规划登记。
