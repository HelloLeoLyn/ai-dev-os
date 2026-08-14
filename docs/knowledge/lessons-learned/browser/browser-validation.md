# Browser Validation Lessons

## LESSON-BRW-001 Infrastructure error 与 assertion failure 必须分离

Category: Browser / Validation
Maturity: AUTOMATED_GUARD

Context: Browser scenario 同时依赖基础设施和页面行为。
Symptom: CDP 连接失败与页面文本错误被显示为同一种失败。
Root Cause: executor 未区分 infrastructure outcome 和 assertion outcome。
Diagnosis: 检查失败发生在 session 建立、action transport 还是 assertion。
Temporary Fix: 在错误摘要中保留原始失败阶段。
Permanent Fix: assertion failure 映射 `FAILED`；browser infrastructure failure 映射 `ERROR` 或 `NOT_AVAILABLE`。
Lesson Learned: 可用性故障和产品缺陷需要不同处置。
Engineering Rule: Browser check 必须保存结构化 failure type。
Automated Guard: `BrowserValidationProviderTest`, `BrowserTestExecutorTest`.
Evidence: `d39c99d`; `BrowserValidationProvider.java`.
Related: [Validation Rules](../../engineering-rules/validation-rules.md).
Next Improvement: UI 按 failure type 提供诊断入口。

## LESSON-BRW-002 Browser Evidence 必须是真实 Artifact

Category: Browser / Validation
Maturity: AUTOMATED_GUARD

Context: 用户级验收需要复核页面状态。
Symptom: 只有 PASS/FAIL 无法证明实际页面、步骤和最终 URL。
Root Cause: evidence 若仅存在内存结果或伪造截图，就无法审计。
Diagnosis: 从 ValidationCheck 追踪 artifact id、scenario、step 与截图内容。
Temporary Fix: 保存失败摘要。
Permanent Fix: screenshot、step result 和 assertion evidence 进入 Artifact；ValidationRun 只保存引用。
Lesson Learned: 浏览器验收必须可复核且不可手工宣告成功。
Engineering Rule: 禁止 fake screenshot、手工 PASS 和在 ValidationRun JSON 内嵌 base64。
Automated Guard: `BrowserValidationProvider`、`ValidationEvidenceService`、真实 Browser E2E。
Evidence: `d39c99d`; `eead21c`; `RealBrowserAcceptanceEndToEndTest.java`.
Related: LESSON-VAL-002.
Next Improvement: 在 runtime 支持时扩展 trace evidence。
