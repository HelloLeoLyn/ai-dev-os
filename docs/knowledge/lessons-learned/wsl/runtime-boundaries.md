# WSL Runtime Lessons

## LESSON-WSL-001 Agent sandbox EPERM 不等于 Host Runtime 故障

Category: WSL / Runtime
Maturity: CONFIRMED

Context: Codex agent sandbox、正常 WSL process 与 production orchestrator 具有不同 network namespace 和权限。
Symptom: sandbox 内 socket 或 netlink probe 返回 EPERM，而宿主 WSL 中 Gateway/CDP 可连接。
Root Cause: agent runtime 的 network isolation（观察到 `bwrap --unshare-net`）限制了 probe。
Diagnosis: 在三个 runtime 中分别验证相同 endpoint，并记录 process owner、namespace 与调用路径。
Temporary Fix: 在正常本机进程环境执行只读 connectivity verification。
Permanent Fix: Not yet implemented.
Lesson Learned: probe 结果只描述执行它的 runtime。
Engineering Rule: 必须区分 agent sandbox、host runtime 与 production runtime；不得为 sandbox 限制错误修改生产代码。
Automated Guard: None；当前依赖诊断纪律。
Evidence: CONFIRMED runtime evidence；`NetworkProbeService.java`; `OpenClawWebSocketClient.java`.
Related: [Execution Rules](../../engineering-rules/execution-rules.md).
Next Improvement: probe 输出增加 runtime identity。
