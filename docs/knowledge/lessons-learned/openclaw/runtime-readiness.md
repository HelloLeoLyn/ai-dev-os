# OpenClaw Runtime Lessons

## LESSON-OCL-001 Gateway connected 不代表 Browser/CDP ready

Category: OpenClaw / Browser
Maturity: LESSON

Context: Browser Acceptance 依赖 Gateway、browser profile、Chromium/CDP、attached tab 与 navigation capability。
Symptom: Gateway WebSocket 可连接，但 Browser provider 仍不可执行真实场景。
Root Cause: 单一 transport 状态被误当成整条 browser capability readiness。
Diagnosis: 分别探测 Gateway、profile、CDP endpoint、attached browser 与真实 navigation。
Temporary Fix: 按层手工检查并报告失败层。
Permanent Fix: Not yet implemented as one aggregate readiness model.
Lesson Learned: 复合 capability 的 READY 必须由所有必要层共同成立。
Engineering Rule: 不得用 Gateway `CONNECTED` 替代 Browser runtime `AVAILABLE`。
Automated Guard: 部分由 Browser availability handling 覆盖，尚无完整分层 guard。
Evidence: `d39c99d`; `eead21c`; `BrowserValidationProvider.java`; `RealBrowserAcceptanceEndToEndTest.java`.
Related: [Browser Design](../../../architecture/browser-design.md).
Next Improvement: 建立分层 readiness response。

## LESSON-OCL-002 Agent session success 不等于 Browser assertion success

Category: OpenClaw / Browser
Maturity: AUTOMATED_GUARD

Context: OpenClaw transport 可以成功结束会话，同时页面 assertion 失败。
Symptom: 旧映射仅依据 agent session 状态，将 assertion failure 映射为 SUCCESS。
Root Cause: transport result 与 business assertion result 共用一个成功信号。
Diagnosis: 比较 agent envelope、step assertion 和最终 ValidationCheck。
Temporary Fix: 从执行结果文本中显式识别 assertion error。
Permanent Fix: Browser envelope 携带 `succeeded`、`errorMessage`，由 assertion 决定 check status。
Lesson Learned: Transport success 不等于业务验收成功。
Engineering Rule: Validation status 必须来自业务结果，不得由连接成功推导。
Automated Guard: `OpenClawBrowserTestExecutor` 与 `BrowserTestExecutorTest` regression cases。
Evidence: `eead21c`; `OpenClawBrowserTestExecutor.java`; `BrowserTestExecutorTest.java`.
Related: LESSON-BRW-001.
Next Improvement: 为所有 remote executor 统一 outcome envelope。
