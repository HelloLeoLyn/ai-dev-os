# 第十三章 常见问题

本章汇总 AI Dev OS v1.0 搭建与运行中的常见问题。
每个问题按「现象 → 原因 → 处理」组织，并关联对应章节。

> 运行期故障还可参考 `docs/troubleshooting/common-errors.md`；
> 运维恢复细节参考 `docs/operation/runbook.md`。

---

## 1. WSL2 问题

### 1.1 WSL1 / WSL2 版本错误

- 现象：发行版显示 `VERSION` 为 1
- 原因：未设置默认版本 2
- 处理：`wsl --set-default-version 2`，再 `wsl --set-version <发行版> 2`
  （详见 `01-windows11.md`）

### 1.2 Ubuntu 启动失败

- 现象：`wsl` 进入时报错或无法启动
- 原因：虚拟化未开、发行版损坏或资源不足
- 处理：确认 BIOS 虚拟化开启；`wsl --shutdown` 后重试；
  检查 `.wslconfig` 内存配置

### 1.3 DNS 解析失败

- 现象：WSL 内无法解析域名
- 原因：`/etc/resolv.conf` 异常
- 处理：确认 `/etc/wsl.conf` 中 `generateResolvConf=true`，
  `wsl --shutdown` 重启

### 1.4 apt 更新慢

- 现象：`apt update` 长时间无响应
- 原因：使用官方源，网络受限
- 处理：切换阿里云等国内镜像源后重试（`02-wsl2-ubuntu.md`）

### 1.5 /mnt/c 性能慢

- 现象：访问 `/mnt/c` 下文件明显卡顿
- 原因：跨文件系统 IO 开销大
- 处理：项目放 WSL 文件系统内（`~/workspace`），`/mnt/c` 仅做数据交换

---

## 2. Docker 问题

### 2.1 docker 命令找不到

- 现象：`docker: command not found`
- 原因：Docker 未安装或未加入 PATH
- 处理：按 `03-docker.md` 安装 Docker Desktop（WSL 集成）或 Docker Engine

### 2.2 docker.sock 权限错误

- 现象：`permission denied ... /var/run/docker.sock`
- 原因：用户不在 docker 组
- 处理：`sudo usermod -aG docker $USER` 后重启 WSL

### 2.3 Docker daemon 未启动

- 现象：`Cannot connect to the Docker daemon`
- 原因：Docker 服务未运行
- 处理：Docker Engine 执行 `sudo systemctl start docker`；
  Docker Desktop 启动应用

### 2.4 镜像拉取失败

- 现象：拉取镜像超时或失败
- 原因：网络到 Docker Hub 不稳定
- 处理：配置 registry-mirrors 或代理后重试（`03-docker.md` 第 7 节）

### 2.5 Testcontainers 启动失败

- 现象：测试中临时容器无法启动
- 原因：测试进程无法访问 docker daemon
- 处理：确认 `docker ps` 正常、当前用户在 docker 组、镜像可拉取

---

## 3. Java / Maven 问题

### 3.1 Java 版本不对

- 现象：`java -version` 不是 21
- 原因：PATH 指向其他 JDK
- 处理：确认 `JAVA_HOME` 指向 JDK 21，重新 `source ~/.bashrc`

### 3.2 Maven 依赖下载慢

- 现象：构建长时间停留在下载依赖
- 原因：官方中央仓库网络慢
- 处理：配置阿里云 mirror（`04-toolchain.md` 第 2.3 节）

### 3.3 mvn 找不到

- 现象：`mvn: command not found`
- 原因：Maven 未加入 PATH
- 处理：确认 `MAVEN_HOME` 配置；或直接使用项目 `./mvnw`

### 3.4 编译失败

- 现象：`./mvnw package` 报编译错误
- 原因：JDK 版本不符或依赖缺失
- 处理：确认 Java 21、依赖可下载；查看具体编译错误定位代码问题

---

## 4. Node / Vue 问题

### 4.1 npm install 失败

- 现象：安装依赖报错或超时
- 原因：registry 不可达或依赖冲突
- 处理：`npm config set registry https://registry.npmmirror.com` 后重试

### 4.2 node 版本不兼容

- 现象：构建报 Node 版本要求不符
- 原因：Node 版本过旧/过新
- 处理：用 nvm 切换到 Node 24.x，`nvm use 24`

### 4.3 npm registry 问题

- 现象：`npm install` 拉包失败
- 原因：默认 registry 网络问题
- 处理：切换 npmmirror，或配置代理

### 4.4 build 失败

- 现象：`npm run build` 报错
- 原因：TypeScript 错误或依赖缺失
- 处理：先修 TypeScript 错误（`vue-tsc --noEmit`），确认依赖完整

---

## 5. PostgreSQL 问题

### 5.1 数据库连接失败

- 现象：应用或 psql 连接失败
- 原因：数据库未启动、连接串错误
- 处理：确认容器/服务运行，核对 `AI_DEV_OS_POSTGRES_URL`

### 5.2 用户密码错误

- 现象：认证失败
- 原因：环境变量密码与建库密码不一致
- 处理：核对 `AI_DEV_OS_POSTGRES_PASSWORD`，必要时重置密码

### 5.3 migration 失败

- 现象：启动时迁移报错
- 原因：权限不足或 SQL 冲突
- 处理：确认用户有建表权限，检查 `schema_migrations` 失败记录

### 5.4 schema_migrations 异常

- 现象：版本记录缺失或 success 为 false
- 原因：迁移中断或重复执行
- 处理：对比 V1～V7 记录，修复后重启；异常时按 runbook 处理

### 5.5 readiness 返回 503

- 现象：`/api/health/readiness` 持续 503
- 原因：迁移未完成或数据库异常
- 处理：检查后端日志、PostgreSQL 连接与迁移状态（`12-verification.md`）

---

## 6. Codex 问题

### 6.1 登录失败

- 现象：`codex login` 失败
- 原因：浏览器无法打开或网络受限
- 处理：重试登录，检查网络/代理

### 6.2 trust 目录问题

- 现象：项目被拒绝执行
- 原因：`trust_level` 未放行
- 处理：在 `config.toml` 设置 `trusted` 或交互式确认（`06-codex-cli.md`）

### 6.3 model 配置错误

- 现象：模型调用 401/404
- 原因：provider 配置或模型名错误
- 处理：核对 `base_url`、`model`、`env_key`

### 6.4 DeepSeek profile 不生效

- 现象：启动仍用 OpenAI 模型
- 原因：profile 未正确加载或有冲突
- 处理：确认 `--profile deepseek` 与 `[profiles.deepseek]`，
  清理重复定义（`07-deepseek.md`）

---

## 7. OpenClaw 问题

### 7.1 Gateway disconnected

- 现象：orchestrator 无法连接 Gateway
- 原因：Gateway 未启动或地址错误
- 处理：确认 `openclaw gateway` 运行，核对 `OPENCLAW_GATEWAY_URL`

### 7.2 token 错误

- 现象：连接被拒绝
- 原因：两端 Token 不一致
- 处理：核对 `OPENCLAW_GATEWAY_TOKEN`

### 7.3 CDP 连接失败

- 现象：无法连接浏览器调试端口
- 原因：调试端口未开或防火墙拦截
- 处理：确认 Chrome 带 `--remote-debugging-port` 启动，放行端口

### 7.4 浏览器未附加

- 现象：任务提示无浏览器可用
- 原因：Chromium 未安装
- 处理：执行 `openclaw install browsers`（`08-openclaw.md`）

### 7.5 Playwright 问题

- 现象：浏览器启动报依赖缺失
- 原因：系统依赖不完整
- 处理：按 Playwright 指引安装系统依赖后重试

---

## 8. MCP 问题

### 8.1 server 启动失败

- 现象：MCP server 无法启动
- 原因：命令不存在或依赖缺失
- 处理：`which <command>` 检查，安装对应 server 包

### 8.2 filesystem 路径错误

- 现象：文件工具访问失败
- 原因：根路径不存在或越权
- 处理：核对 `.mcp/config.json` 的 `args` 路径

### 8.3 Docker MCP 权限问题

- 现象：Docker 工具报权限错误
- 原因：daemon 不可访问或未接入 server
- 处理：确认 `docker ps` 可用，按需接入 docker server

### 8.4 工具列表为空

- 现象：看不到任何 MCP 工具
- 原因：`tools.mcp.enabled` 未开启或配置未生效
- 处理：显式开启后重启 Agent / orchestrator，重新检查

---

## 9. AI Dev OS 运行问题

### 9.1 后端启动失败

- 现象：后端启动即退出
- 原因：JDK 版本、依赖或数据库问题
- 处理：查看启动日志，按第 3 / 5 节排查

### 9.2 前端无法访问

- 现象：`15174` 页面打不开
- 原因：Vite 未启动或端口被占用
- 处理：确认 `npm run dev` 正常、`ss -ltnp` 检查端口

### 9.3 端口冲突

- 现象：`18080` / `15174` / `18789` / `5432` 被占用
- 原因：已有进程监听
- 处理：`ss -ltnp` 定位，停止冲突进程或改端口

### 9.4 Agent 流程异常

- 现象：计划/Job/审计流程卡住或报错
- 原因：worker lease、outbox 或数据库异常
- 处理：查看后端日志与 `docs/operation/runbook.md` 故障恢复流程

---

## 10. 排查原则

按以下顺序排查，避免跳跃：

1. **先看日志**
   - 后端：`services/orchestrator` 运行日志
   - 前端：Vite 终端输出
   - 数据库：PostgreSQL 日志
   - Gateway：OpenClaw 日志

2. **再确认环境**
   - WSL2 / Docker / Java / Node 版本
   - 服务进程与端口监听

3. **再验证依赖**
   - PostgreSQL 连接
   - Docker daemon
   - Maven / npm 网络可达

4. **最后检查配置**
   - 环境变量（`AI_DEV_OS_*`、`OPENCLAW_*`、`CODEX_*`）
   - 配置文件（`.mcp/config.json`、`~/.codex/config.toml`）
   - 权限设置（docker 组、MCP 权限、trust_level）

> 排查时一次只改一个变量，验证后再改下一个，避免叠加干扰。
