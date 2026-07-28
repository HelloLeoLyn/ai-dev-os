# AI Dev OS Docker MCP Design

## 1. 目标

为 AI Agent 提供 Docker 环境管理能力。

通过 MCP 协议，让 Codex 可以安全访问本地 Docker Engine。

原则：

- Agent 负责分析和决策
- MCP 负责执行 Docker 操作
- 高风险操作需要人工确认

---

## 2. 架构

```text
Codex Agent
    |
Docker MCP Server
    |
Docker Engine
    |
Containers / Images / Networks
```

---

## 3. 安装

安装：

```bash
npm install -g @paretools/docker
```

版本：

```text
@paretools/docker 0.21.1
```

启动命令：

```bash
pare-docker
```

---

## 4. Codex 配置

添加：

```bash
codex mcp add docker -- pare-docker
```

查看：

```bash
codex mcp list
```

---

## 5. 支持能力

Docker MCP 提供：

- ps
- images
- logs
- inspect
- compose_ps
- compose_up
- compose_down

---

## 6. 权限策略

默认只读：

- 查看容器
- 查看日志
- 查看镜像
- 查看状态

需要确认：

- 启动容器
- 停止容器
- compose 操作

高风险：

- 删除容器
- 删除镜像
- 删除 volume
- docker system prune

---

## 7. 验证记录

Codex 已加载 Docker MCP tools。

测试：

```text
请通过 Docker MCP 查询当前运行中的容器，只读取，不执行任何修改操作。
```

调用：

```text
docker.ps
```

结果：

```text
0 containers (0 running, 0 stopped)
```

验证：

- Docker MCP 连接成功
- Docker Engine 访问成功
- 只读查询正常

---

## 8. 总结

Docker MCP 为 AI Dev OS 提供环境管理能力。

当前 AI Agent 已具备：

- 文件管理能力
- Git 版本管理能力
- 浏览器自动化能力
- Docker 环境管理能力

形成开发闭环：

```text
需求分析
↓
代码修改
↓
版本管理
↓
环境启动
↓
自动测试
↓
结果反馈
```
