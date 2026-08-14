# Execution Engineering Rules

1. Agent sandbox failure、host runtime failure 与 production runtime failure 必须独立诊断。
2. Codex sandbox 的 socket/netlink EPERM 不得作为修改生产网络代码的充分证据。
3. Local runtime capability 必须从实际 orchestrator process context 独立探测。
4. Gateway、Browser profile、CDP、attached browser 与 navigation capability 必须分层判断 readiness。
5. Tool capability 和 READ_ONLY 必须由 Orchestrator policy 强制，不能依赖 prompt 自律。
6. OpenClaw 是受控 execution capability，不是 Task、Validation、Policy 或 architecture authority。
7. 一个 capability 的成功不得提升另一个 capability 的权限。

实现依据：`McpToolRouter`、`BrowserValidationProvider`、`OpenClawWebSocketClient` 及相关测试。
