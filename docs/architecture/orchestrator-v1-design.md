# AI Dev OS Orchestrator V1 Design

版本： v1.0

# 1. 目标

实现 AI Dev OS 第一个可运行的任务编排核心。

第一版不直接控制真实 Agent，而是验证：

- 配置读取
- Workflow解析
- Agent调度
- 执行记录

---

# 2. 第一版范围

包含：

- YAML配置加载
- Workflow流程解析
- Agent定义读取
- 顺序任务执行
- 执行日志输出

暂不包含：

- 多Agent并行
- 自动决策
- 分布式执行
- 复杂权限系统

---

# 3. 项目结构

    services/
    └── orchestrator/

        ├── config/
        │   ├── AgentConfigLoader
        │   └── WorkflowConfigLoader
        │
        ├── engine/
        │   ├── WorkflowEngine
        │   └── TaskExecutor
        │
        ├── agent/
        │   └── AgentManager
        │
        └── log/
            └── ExecutionLogger

---

# 4. 核心流程

    用户任务

    ↓

    Orchestrator接收

    ↓

    加载workflow配置

    ↓

    读取Agent定义

    ↓

    检查执行策略

    ↓

    执行任务步骤

    ↓

    记录结果

    ↓

    生成报告

---

# 5. 核心模块

## Workflow Engine

职责：

- 读取workflow
- 按步骤执行
- 管理任务状态

## Agent Manager

职责：

- 加载Agent配置
- 判断Agent能力
- 分配任务

## Policy Checker

职责：

- 检查权限等级
- 判断是否需要确认

## Execution Logger

职责：

- 记录执行过程
- 保存执行结果

---

# 6. 第一版示例

输入：

    开发一个用户查询接口

流程：

    Hermes

    ↓

    生成开发计划

    ↓

    Codex

    ↓

    修改代码

    ↓

    Tester

    ↓

    执行测试

    ↓

    生成报告

---

# 7. 技术方案

推荐：

- Java 21
- Spring Boot
- SnakeYAML
- Jackson
- Maven

---

# 8. 后续扩展

第二阶段：

接入真实 Agent。

第三阶段：

接入 MCP。

第四阶段：

支持多任务并行。

第五阶段：

增加 Web 管理界面。
