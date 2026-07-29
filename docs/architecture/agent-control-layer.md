# AI Dev OS Agent Control Layer

版本： v1.0

---

# 1. 设计目标

Agent Control Layer 是 AI Dev OS 中位于 Agent 与底层工具之间的控制层。

目标：

- 统一管理不同 Agent 的执行行为
- 将权限策略与具体 Agent 解耦
- 保证任务执行过程可追踪
- 降低 Agent 自主执行风险

---

# 2. 为什么需要 Control Layer

单独依赖 Agent 自身权限控制存在限制。

例如：

Codex 可以通过 approval_policy 控制命令执行风险。

但是：

- 无法理解项目业务规则
- 无法知道哪些文件属于核心模块
- 无法统一管理多个 Agent 行为

因此 AI Dev OS 增加控制层。

---

# 3. 架构关系

    User Request

          ↓

    Planner Agent

          ↓

    Agent Control Layer

          ↓

    Execution Policy

          ↓

    Agent

          ↓

    MCP Tools

          ↓

    System Resource

---

# 4. Control Layer职责

## 4.1 任务检查

负责：

- 分析任务类型
- 判断风险等级
- 加载执行策略

---

## 4.2 权限判断

根据 Execution Profile 判断：

Level 0：

自动执行。

例如：

- 查看文件
- 查询 Git 状态
- 查看日志

Level 1：

请求确认。

例如：

- 修改代码
- 创建文件
- 提交代码

Level 2：

强制人工确认。

例如：

- 删除数据
- 推送代码
- 修改系统配置

---

# 5. Agent统一管理

不同 Agent：

    Hermes
     |
    需求分析

    Codex
     |
    代码实现

    OpenClaw
     |
    浏览器和GUI操作

    Tester
     |
    测试验证

都必须经过 Control Layer。

---

# 6. 执行流程

    任务输入

    ↓

    Planner 分析

    ↓

    Control Layer 检查权限

    ↓

    调用 Agent

    ↓

    调用 MCP

    ↓

    执行操作

    ↓

    记录结果

    ↓

    生成报告

---

# 7. 设计原则

## Agent负责能力

例如：

- 写代码
- 测试
- 浏览器操作

## Control Layer负责规则

例如：

- 是否允许执行
- 是否需要确认
- 是否记录日志

## MCP负责资源访问

例如：

- 文件
- Git
- Docker
- 浏览器

---

# 8. 后续实现方向

阶段1：

完成策略文件定义。

阶段2：

增加任务执行前检查。

阶段3：

增加执行日志。

阶段4：

支持多 Agent 协作。

---

# 总结

AI Dev OS 不依赖单个 Agent 的安全能力。

Agent 是执行者。

Control Layer 是管理者。

MCP 是工具层。

三者共同组成可靠的 AI 开发操作系统。
