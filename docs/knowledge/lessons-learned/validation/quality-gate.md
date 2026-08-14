# Quality Gate Lessons

## LESSON-QGT-001 Quality Gate 必须在后端 Git 入口 enforcement

Category: Validation / Git
Maturity: AUTOMATED_GUARD

Context: UI 仅是一个客户端，不能构成权限边界。
Symptom: 隐藏按钮后，调用 API 或服务仍可能执行 Git write。
Root Cause: Gate 只影响展示，没有进入正式写操作调用链。
Diagnosis: 直接调用 ChangeSet、Commit、Push 服务验证拒绝。
Temporary Fix: 暂停自动 Git 操作。
Permanent Fix: `ChangeService`、`CommitService`、`RemoteGitService` 调用 `QualityGateService.assertAllowed(taskId)`。
Lesson Learned: 安全控制必须位于权威执行边界。
Engineering Rule: 无当前 Gate PASS，不允许 automated task-scoped Git write。
Automated Guard: `QualityGateGitEnforcementTest`.
Evidence: `8b2600c`; corresponding service classes.
Related: [Git Rules](../../engineering-rules/git-rules.md).
Next Improvement: 对所有新增 Git write entry 建 contract test。

## LESSON-QGT-002 Gate PASS 不提升 ExecutionMode

Category: Validation / Execution
Maturity: AUTOMATED_GUARD

Context: Quality Gate 表示 evidence 满足质量要求，ExecutionMode 表示操作权限。
Symptom: READ_ONLY task 因 Gate PASS 获得 commit/push 权限。
Root Cause: 将资格判定误用为权限授予。
Diagnosis: READ_ONLY + PASS 后直接尝试 ChangeSet、Commit、Push。
Temporary Fix: 在 Git service 再检查 execution mode。
Permanent Fix: Gate 与 READ_ONLY policy 分层执行，两者均须通过。
Lesson Learned: 质量资格不能覆盖最小权限。
Engineering Rule: READ_ONLY 永远不能通过 Gate 获得 workspace/Git write。
Automated Guard: Gate enforcement tests、`McpToolRouterTest`、READ_ONLY E2E。
Evidence: `8b2600c`; `QualityGateGitEnforcementTest.java`.
Related: LESSON-VAL-003.
Next Improvement: 输出组合拒绝原因而不暴露内部细节。

## LESSON-QGT-003 新 ValidationRun 不得继承旧 Gate

Category: Validation / Git
Maturity: AUTOMATED_GUARD

Context: 新 validation evidence 可能与旧 run 完全不同。
Symptom: 旧 PASS 被错误用于授权新 run 的 Git write。
Root Cause: Gate 只按 taskId 查询，未绑定证据版本。
Diagnosis: 创建新 run 后复用旧 gate 并尝试写操作。
Temporary Fix: 每次 rerun 后手工重评 Gate。
Permanent Fix: Gate result 绑定 validationRunId、policyVersion 与 evidence fingerprint。
Lesson Learned: 授权只对被评估的证据快照有效。
Engineering Rule: rerun 必须产生并使用新 Gate。
Automated Guard: `QualityGateServiceTest` 的 rerun/idempotency cases。
Evidence: `8b2600c`; `QualityGateResult.java`; `QualityGateService.java`.
Related: LESSON-SEC-006.
Next Improvement: 显式展示 stale gate reason。
