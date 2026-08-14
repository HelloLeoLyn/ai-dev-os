# Network Engineering Rules

1. **Loopback always DIRECT.** `localhost`、`127.0.0.1`、`::1` 及平台本地 capability 端口不得进入外部代理。
2. **DIRECT clears inherited proxies.** 受控子进程必须显式删除大小写 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY`，并生成安全的 `NO_PROXY/no_proxy`。
3. **配置必须动态生效。** 新 process 和新 HTTP/WebSocket client 必须按当前配置或配置版本构造；不得永久持有旧 host 的 ProxySelector。
4. **AUTO failure is explicit.** Windows host 无法从首条有效 default route 解析时返回 `AUTO_WINDOWS_HOST_RESOLUTION_FAILED`，不得回退历史 host、第二条路由或硬编码地址。
5. **Local 与 external 分开诊断。** Probe 必须标识 DIRECT、PROXY 或 FAILED，不能用 external connectivity 推断 local runtime readiness。
6. **Credential must be redacted.** proxy URI 的 user-info 不得进入 API、Audit、UI 或日志。

实现依据：`ProxyEnvironmentService`、`NetworkAwareHttpClientFactory`、`WindowsHostResolver`、`NetworkProbeService`（commit `dca098e`）。
