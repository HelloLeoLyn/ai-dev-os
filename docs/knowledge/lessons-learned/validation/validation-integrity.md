# Validation Integrity Lessons

## LESSON-VAL-001 Validation status 必须语义严格

Category: Validation
Maturity: AUTOMATED_GUARD

Context: provider 可能成功、断言失败、基础设施错误、不适用或不可用。
Symptom: 不同问题全部显示 FAILED，或不可用被误报 SUCCESS。
Root Cause: 状态模型不足或 provider 映射不一致。
Diagnosis: 同时检查 provider availability、execution outcome、applicability 与 assertion。
Temporary Fix: 在 summary 中保留原因。
Permanent Fix: 使用 SUCCESS、FAILED、ERROR、SKIPPED，并以 metadata 区分 NOT_AVAILABLE/NOT_APPLICABLE。
Lesson Learned: 状态决定后续 Gate 和诊断，不能模糊。
Engineering Rule: 每种 outcome 只能映射到其真实语义。
Automated Guard: Validation、Security、Browser provider tests。
Evidence: `9c9efd7`; `6b6f783`; `d39c99d`; `ValidationStatus.java`.
Related: LESSON-SEC-002、LESSON-BRW-001.
Next Improvement: 评估将 availability 提升为强类型 status。

## LESSON-VAL-002 Validation 必须留下 Evidence

Category: Validation
Maturity: AUTOMATED_GUARD

Context: PASS/FAIL 本身不能回答执行了什么、为何失败。
Symptom: 无法复核 command、exit code、duration、log 或 report。
Root Cause: 只持久化汇总 decision。
Diagnosis: 从 ValidationCheck 追踪 Artifact references 和 execution metadata。
Temporary Fix: 保存受控日志摘要。
Permanent Fix: 大内容走 Artifact，run/check 保存 command、exitCode、duration、summary 与引用。
Lesson Learned: 可审计 evidence 是 Validation 的组成部分。
Engineering Rule: 无 evidence 的真实 Validation 不得宣告完成。
Automated Guard: `ValidationEvidenceService`、repository tests、真实 E2E。
Evidence: `9c9efd7`; `ValidationCenterEndToEndTest.java`.
Related: LESSON-BRW-002、LESSON-SEC-004.
Next Improvement: 为 artifact retention 增加 policy。

## LESSON-VAL-003 READ_ONLY 必须客观验证 Git 状态

Category: Validation / Git
Maturity: AUTOMATED_GUARD

Context: prompt 或 Agent 声明无法证明 workspace 未改变。
Symptom: 执行报告称只读，但目标仓库可能产生文件或 index/worktree 变化。
Root Cause: 权限意图被当成执行证据。
Diagnosis: 前后比较 HEAD、`status --porcelain` 与 `diff --binary`。
Temporary Fix: 使用临时 Git workspace 并人工比较。
Permanent Fix: 真实 Security、Browser 和 Validation E2E 自动断言三项完全一致。
Lesson Learned: READ_ONLY 是可验证不变量。
Engineering Rule: 真实只读验收必须使用客观 Git evidence。
Automated Guard: `RealSecurityScannerEndToEndTest`, `RealBrowserAcceptanceEndToEndTest`, ToolRouter policy。
Evidence: `deeb1fb`; `eead21c`; `McpToolRouterTest.java`.
Related: LESSON-BRW-003、LESSON-QGT-002.
Next Improvement: 统一 snapshot helper 并覆盖 untracked files。
