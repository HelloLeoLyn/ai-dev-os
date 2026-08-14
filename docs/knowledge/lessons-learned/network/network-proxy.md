# Network / Proxy Lessons

## LESSON-NET-001 Loopback 请求不得进入外部代理

Category: Network
Maturity: AUTOMATED_GUARD

Context: OpenClaw Gateway、CDP、PostgreSQL 与 Orchestrator 使用本机地址。
Symptom: 本地服务实际可用，但请求出现代理返回的 503 或超时。
Root Cause: WSL 继承的 proxy environment 或 HTTP client proxy selector 将 loopback 请求发送到外部代理。
Diagnosis: 分别探测本地监听、DIRECT 请求与带代理环境的请求，不能只看服务进程。
Temporary Fix: 为 `localhost`、`127.0.0.1`、`::1` 配置 `NO_PROXY`。
Permanent Fix: Runtime Network / Proxy Configuration 统一生成本地 DIRECT 路由。
Lesson Learned: 服务连通性和请求路由是两个独立事实。
Engineering Rule: Loopback 与平台本地端口始终 DIRECT。
Automated Guard: `ProxyEnvironmentService`、`NetworkAwareHttpClientFactory` 和 network probe 测试。
Evidence: `dca098e`; `services/orchestrator/src/main/java/com/aidevos/orchestrator/network/`; `ProxyEnvironmentServiceTest.java`.
Related: [Network Rules](../../engineering-rules/network-rules.md).
Next Improvement: 将更多本地 capability 纳入统一 probe。

## LESSON-NET-002 DIRECT 必须清除继承代理

Category: Network
Maturity: AUTOMATED_GUARD

Context: 子进程会继承父进程环境。
Symptom: 配置显示 DIRECT，命令却仍通过父进程代理访问。
Root Cause: “不新增 proxy”不会删除已继承的大小写 proxy variables。
Diagnosis: 检查 CommandExecutor 最终环境，而非只检查应用配置。
Temporary Fix: 启动命令前手工 unset proxy variables。
Permanent Fix: DIRECT 显式清除 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY` 及小写形式，仅保留安全 `NO_PROXY`。
Lesson Learned: DIRECT 是主动路由决策，不是缺省配置。
Engineering Rule: 所有受控命令必须获得显式 DIRECT environment。
Automated Guard: `ProxyEnvironmentServiceTest` 验证继承代理被清除。
Evidence: `dca098e`; `CommandExecutor.java`; `ProxyEnvironmentService.java`.
Related: LESSON-NET-001.
Next Improvement: 为新增 executor 建立统一 contract test。

## LESSON-NET-003 WSL default route 不保证包含 via

Category: Network / WSL
Maturity: AUTOMATED_GUARD

Context: `AUTO_WINDOWS_HOST` 通过 WSL 首条有效 default route 解析 Windows Host。
Symptom: 当前网络模式的首条路由形如 `default dev eth0`，无法得到 gateway。
Root Cause: 路由解析若假定 `default via <private-ip>`，会在部分 WSL 网络模式失效。
Diagnosis: 读取路由原始输出并只判断首条有效 default route 是否含 `via`。
Temporary Fix: 使用 MANUAL、SYSTEM 或 DIRECT。
Permanent Fix: Not yet implemented；Windows Host Discovery V2 可评估其他经验证来源。
Lesson Learned: 不完整的发现结果不能用猜测补齐。
Engineering Rule: 失败返回 `AUTO_WINDOWS_HOST_RESOLUTION_FAILED`；不得选择第二条路由、历史 host 或硬编码私网地址。
Automated Guard: `WindowsHostResolverTest.neverFallsBackToSecondaryDefaultRoute`。
Evidence: `dca098e`; `WindowsHostResolver.java`; `WindowsHostResolverTest.java`.
Related: [Network Rules](../../engineering-rules/network-rules.md).
Next Improvement: 单独规划 Windows Host Discovery V2。

## LESSON-NET-004 apt 等待不能直接归因于 Ubuntu 官方源

Category: Network / Tooling
Maturity: OBSERVED

Context: 多个官方与第三方 repository 共同参与 apt metadata 更新。
Symptom: `apt update` 长时间停留在 waiting for headers。
Root Cause: 历史观察表明 NodeSource、Docker 等第三方源可能独立拖慢流程；具体事件未由仓库测试复现。
Diagnosis: 按 `archive.ubuntu.com`、`security.ubuntu.com` 与第三方 source 分组检查响应。
Temporary Fix: 暂时禁用已确认故障的单一第三方 source。
Permanent Fix: Not yet implemented.
Lesson Learned: 总体进度不能定位具体失败源。
Engineering Rule: 更换 Ubuntu 源前必须先取得分源诊断证据。
Automated Guard: None.
Evidence: HISTORICAL / OBSERVED；仓库无自动测试。
Related: [Troubleshooting](../../../troubleshooting/common-errors.md).
Next Improvement: 增加 repository health diagnostics。
