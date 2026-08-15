# EPR-001 — Browser timer Illegal invocation

- **Problem ID:** EPR-001
- **Title:** Browser timer Illegal invocation
- **Status:** VERIFIED
- **Severity:** HIGH
- **Category:** Browser Runtime
- **Detected At:** AI Dev OS Task polling E2E

## Context

Task Center 使用 `TaskPollingMonitor` 轮询 Task 状态。浏览器宿主 API 的调用必须保留正确的 host receiver。

## Symptoms

`taskPollingMonitor.ts` 在 `scheduleNext()` / `poll()` 路径抛出 `TypeError: Illegal invocation`。后端 Task、Plan 和 Approval 实际已成功。

## Trigger / Reproduction

在创建并规划、Approve Plan 后启动 Task polling；当 `setTimeout` 或 `clearTimeout` 以脱离 host context 的函数引用调用时稳定复现。

## Impact

前端误报未完成或停止轮询，掩盖真实后端结果。

## Root Cause

浏览器原生 timer 方法被保存/传递后脱离正确调用上下文使用。浏览器宿主方法不是普通无 receiver 的纯函数。

## Contributing Factors

- 调度逻辑把 host API 当作可任意解绑的函数。
- 测试环境中的 Node/Vitest timer 不一定复现浏览器 receiver 约束。

## Why Existing Tests Missed It

既有测试验证了轮询时间和停止条件，但没有使用会拒绝错误 receiver 的 browser-like host stub。

## Resolution

通过 wrapper / `globalThis` 正确调用 timer API，保持 polling 业务逻辑不变。

## Files / Components Affected

- `TaskPollingMonitor`
- 前端 polling tests

## Verification Evidence

- Task polling regression tests passed。
- 前端 `npm test` / `npm run build` passed。
- 真实 E2E 不再出现 Illegal invocation。

## Prevention

### Engineering Guardrails

- Browser host API 必须通过 receiver-safe wrapper 调用。
- 不要把 `window.setTimeout` / `window.clearTimeout` 作为裸函数长期保存。

### Required Regression Tests

- 错误 receiver 的 browser-like timer stub。
- `scheduleNext`、cancel、重复 polling 和页面卸载。

### Detection Signals

浏览器控制台出现 `Illegal invocation`，同时后端状态已推进。

### Do Not Fix By

- 放宽 polling 超时。
- 隐藏异常或重复创建 Task。
- 修改后端 Task 状态。

## Generalized Lesson

Browser host APIs require correct invocation context。

## Related Problems

EPR-006（前端必须准确表达后端实际状态）。

## Related Commits / WorkItems

Task polling Illegal invocation 修复；相关 Task Polling regression tests。

## Future Follow-ups

将 receiver-safe browser API wrapper 纳入前端基础设施规范。
