# AI Dev OS Runtime Orchestrator Design

版本： v1.0

# 1. 目标

Runtime Orchestrator 负责读取 AI Dev OS 配置，并根据任务流程调度 Agent。

负责：

- 加载 Agent 定义
- 加载执行策略
- 加载 Workflow
- 调度 Agent
- 记录执行结果

---

# 2. 架构

    User Request

    ↓

    Runtime Orchestrator

    ↓

    Workflow Engine

    ↓

    Agent Controller

    ↓

    MCP Tools

    ↓

    System Resource

---

# 3. 输入配置

Orchestrator读取：

    configs/

    ├── agents/
    │   ├── hermes.yaml
    │   ├── codex.yaml
    │   ├── tester.yaml
    │   └── openclaw.yaml
    │

    ├── execution/
    │   └── development.yaml
    │

    └── workflows/
        └── development-workflow.yaml

---

# 4. 执行流程

    接收任务

    ↓

    解析 Workflow

    ↓

    选择 Agent

    ↓

    检查 Permission Policy

    ↓

    执行任务

    ↓

    记录结果

    ↓

    进入下一步骤

---

# 5. 核心模块

## Workflow Engine

负责：

- 流程解析
- 步骤调度
- 状态管理

## Agent Manager

负责：

- Agent加载
- Agent能力检查
- Agent权限校验

## Policy Engine

负责：

- 判断操作等级
- 请求确认
- 阻止危险操作

## Task Logger

负责：

- 记录执行过程
- 保存结果
- 生成报告

---

# 6. 第一版实现范围

v0.1：

- YAML配置读取
- 流程顺序执行
- Agent角色校验
- 简单日志记录

暂不实现：

- 分布式执行
- 自动决策
- 多Agent并行

---

# 7. 目标

最终：

用户输入需求。

Orchestrator自动：

1.  调用 Hermes 分析
2.  等待确认
3.  调用 Codex 开发
4.  调用 Tester 验证
5.  调用 OpenClaw 页面测试
6.  生成任务报告
