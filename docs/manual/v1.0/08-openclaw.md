# 08 OpenClaw 配置与使用

本章配置 OpenClaw。OpenClaw 是 AI Dev OS 中的执行 Agent，
负责浏览器自动化、GUI 操作、测试执行与环境操作。

> 前提：已完成 `02-wsl2-ubuntu.md`，网络与基础工具可用。

---

## 1. OpenClaw 在 AI Dev OS 中的角色

职责：

- 浏览器自动化：页面访问、点击、输入、截图、验证
- GUI 操作：桌面级界面操作
- 测试执行：配合自动化测试与页面验证
- 环境操作：按任务执行环境相关动作

与 Codex / Hermes 的关系：

- Hermes：理解需求、制定计划（不直接执行）
- Codex：代码编写与修改
- OpenClaw：浏览器 / GUI / 测试执行

协作流程：

```text
需求 → Hermes 计划 → 人工确认 → Codex 改代码 + OpenClaw 执行验证 → 测试 → 报告
```

orchestrator 通过 WebSocket Gateway 调用 OpenClaw，
相关配置（`application.properties`）：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `openclaw.gateway-url` | `ws://127.0.0.1:18789` | Gateway 地址 |
| `openclaw.token` | 空 | Gateway 认证 Token |
| `openclaw.request-timeout` | `130s` | 请求超时 |
| `openclaw.agent-wait-timeout` | `120s` | Agent 等待超时 |

---

## 2. 安装

OpenClaw 按官方文档安装（支持二进制与源码方式）：

```bash
# 确认 CLI 可用（以官方安装结果为准）
openclaw --version
```

安装要点：

- 在 WSL2 发行版内安装，与 orchestrator 同环境
- 首次使用浏览器能力时安装 Playwright 与 Chromium：

```bash
openclaw install browsers
# 或按官方指引安装 Playwright 依赖
```

> 具体安装命令以 OpenClaw 官方文档为准，版本更新时以官方为准。

---

## 3. Gateway 配置

启动 OpenClaw Gateway 模式，监听 WebSocket：

```bash
openclaw gateway
```

默认地址：

```text
ws://127.0.0.1:18789
```

orchestrator 侧环境变量：

```bash
export OPENCLAW_GATEWAY_URL=ws://127.0.0.1:18789
```

注意事项：

- Gateway 与 orchestrator 在同一 WSL2 实例内时，`127.0.0.1` 互通
- 若跨主机部署，需改为可达地址并开放防火墙端口
- 启动顺序：先启动 OpenClaw Gateway，再启动 orchestrator

---

## 4. Token 认证

为 Gateway 设置认证 Token：

```bash
export OPENCLAW_GATEWAY_TOKEN='<强随机Token>'
openclaw gateway
```

orchestrator 侧配置相同 Token：

```bash
export OPENCLAW_GATEWAY_TOKEN='<强随机Token>'
```

要求：

- 两端 Token 必须一致
- 使用强随机值，不写死到代码或仓库
- 生产环境通过密钥管理注入

---

## 5. Browser 能力

OpenClaw 浏览器能力基于：

- **Playwright**：浏览器自动化框架
- **Chromium**：默认驱动浏览器
- **CDP**（Chrome DevTools Protocol）：底层调试协议
- **Remote Debugging**：连接外部浏览器调试端口

验证浏览器可用：

```bash
openclaw browser test
# 或通过任务触发一次页面访问验证
```

依赖确认：

```bash
# Playwright 浏览器已安装（示例路径）
ls ~/.cache/ms-playwright/
```

---

## 6. 与本地浏览器连接说明

### 6.1 场景一：WSL2 内 Chromium（推荐）

- OpenClaw 直接驱动 Playwright 管理的 Chromium
- 无需连接 Windows 浏览器，配置最简单
- 适合自动化测试与无头验证

### 6.2 场景二：Windows Chrome + WSL2 OpenClaw

Windows 侧以远程调试模式启动 Chrome：

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
  --remote-debugging-port=9222 `
  --user-data-dir=C:\tmp\chrome-debug
```

WSL2 内连接：

- WSL2 访问 Windows 宿主机需使用宿主机 IP（非 `localhost`）
- 获取宿主机 IP：

```bash
cat /etc/resolv.conf | grep nameserver
# 或
ip route show default | awk '{print $3}'
```

- Windows 防火墙需放行调试端口（如 9222）

### 6.3 常见连接方式总结

| 方式 | 说明 | 适用场景 |
| --- | --- | --- |
| WSL2 内 Chromium | Playwright 托管，无需外部浏览器 | 自动化测试（推荐） |
| CDP 连接 Windows Chrome | 通过 `--remote-debugging-port` | 复用本地登录态/真实浏览器 |
| 连接容器内浏览器 | 容器暴露调试端口 | 隔离环境验证 |

---

## 7. MCP / 工具调用关系

- orchestrator 将 OpenClaw 作为执行 Agent 调用，通过 Gateway 下发任务
- MCP 提供工具能力（文件、Git 等），供 Agent 决策与操作
- OpenClaw 的浏览器操作作为执行能力，与 MCP 工具互补

配置关系：

- `.mcp/config.json`：MCP 服务器清单与权限（见 `09-mcp.md`）
- `tools.mcp.enabled`：orchestrator 侧 MCP 工具开关
- 权限遵循 `docs/operation/agent-workflow.md`：涉及登录、提交、
  数据修改的操作需确认

---

## 8. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| Gateway disconnected | Gateway 未启动或地址错误 | 确认 `openclaw gateway` 运行，核对 `OPENCLAW_GATEWAY_URL` |
| CDP 连接失败 | 调试端口未开或防火墙拦截 | 确认 Chrome 带 `--remote-debugging-port` 启动，放行端口 |
| 浏览器未附加 | Chromium 未安装或 Playwright 依赖缺失 | 执行 `openclaw install browsers`，确认 `~/.cache/ms-playwright/` |
| token 错误 | 两端 Token 不一致 | 核对 orchestrator 与 Gateway 的 `OPENCLAW_GATEWAY_TOKEN` |
| 端口冲突（18789） | 端口被占用 | `ss -ltnp` 检查，改端口并同步两端配置 |
| 宿主机连接不通 | WSL2 NAT 使用宿主机 IP | 用 `ip route show default` 获取宿主机 IP，避免用 `localhost` |
| 页面操作超时 | 页面慢或等待时间不足 | 调大 `openclaw.request-timeout` 与 `agent-wait-timeout` |
| 登录态页面验证失败 | 无头浏览器无登录态 | 用 CDP 连接 Windows Chrome 复用会话，或配置持久化 profile |
