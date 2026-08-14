# Git Engineering Rules

1. Quality Gate 必须在 ChangeSet、Commit、Push 的后端正式入口 enforcement；隐藏 UI 不构成控制。
2. 没有当前有效 Gate 的 automated task-scoped Git write 必须拒绝。
3. READ_ONLY 永远不能因 Gate PASS 获得 workspace write、commit 或 push 权限。
4. Git write 授权必须绑定当前 validationRunId、policyVersion 与 evidence fingerprint；rerun 后旧 Gate 失效。
5. Push 必须使用当前 Task evidence，而不是 Agent 声明的“tests passed”。
6. 长期 WSL 项目推荐 project-scoped SSH identity；private key 和 credential 不得进入仓库。
7. 人工运维入口与 automated task-scoped Git operation 必须明确区分。

实现依据：`QualityGateService`、`ChangeService`、`CommitService`、`RemoteGitService` 与 `QualityGateGitEnforcementTest`（commit `8b2600c`）。
