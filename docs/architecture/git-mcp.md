# Git MCP Design

## 1. 目标

为 AI Agent 提供 Git 版本管理能力。

通过 MCP 协议，让 Codex 可以安全访问 Git 仓库。

原则：

- Agent 负责分析和决策
- MCP 负责执行 Git 操作
- 高风险操作需要人工确认

---

## 2. 架构

```text
Codex Agent

    |

Git MCP Server

    |

Git Repository
```

说明：

- Codex 负责提出 Git 操作需求
- Git MCP 负责调用 Git 能力
- Git Repository 保存项目版本状态

---

## 3. 安装

安装：

```bash
npm install -g @cyanheads/git-mcp-server
```

版本：

```text
@cyanheads/git-mcp-server 2.15.1
```

---

## 4. Codex 配置

添加 Git MCP：

```bash
codex mcp add git -- git-mcp-server /home/administrator/workspace/ai-dev-os
```

查看配置：

```bash
codex mcp list
```

结果：

```text
filesystem  mcp-server-filesystem
git         git-mcp-server
```

---

## 5. 支持能力

Git MCP 提供 Git 操作能力：

只读：

- git status
- git log
- git diff

版本管理：

- branch
- commit
- merge
- stash

远程操作：

- pull
- push

---

## 6. 权限策略

### 默认允许

只读操作：

- 查看状态
- 查看历史
- 查看差异

### 需要人工确认

修改仓库：

- commit
- branch
- merge
- push

### 高风险操作

必须人工确认：

- reset
- clean
- force push

---

## 7. 验证记录

测试：

> 请通过 Git MCP 查看当前仓库状态，只读取，不修改任何文件。

结果：

- Codex 调用 git_status MCP 工具
- 返回当前分支 main
- 工作区状态 clean
- 未修改任何文件

验证完成。
