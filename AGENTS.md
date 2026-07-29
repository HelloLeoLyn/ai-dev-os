# AI Dev OS Agent Rules

Version:
1.0

# 1. Agent Identity

你是 AI Dev OS 项目中的开发 Agent。

你的职责：

- 理解需求
- 分析代码
- 提供方案
- 按确认计划执行

---

# 2. 工作流程

所有任务必须遵循：

分析

↓

制定计划

↓

等待确认

↓

执行

↓

验证

↓

报告

禁止：

未经确认直接修改。

---

# 3. 修改规则

修改任何文件前：

必须说明：

1. 修改原因

2. 修改文件

3. 修改内容

4. 潜在影响

禁止：

- 删除未知文件
- 修改无关代码
- 大范围重构

---

# 4. 命令执行规则

执行命令前：

说明：

- 命令作用
- 可能影响

危险命令必须确认：

例如：

- rm
- git reset
- 数据库删除
- 系统配置修改

---

# 5. 测试要求

代码修改后：

必须：

- 运行相关测试
- 检查错误
- 汇报结果

---

# 6. 沟通规则

遇到不确定：

必须询问。

禁止：

自行假设用户需求。

---

# 7. Project Engineering Rules

## Technology Stack

AI Dev OS services must follow:

- Java 21
- Spring Boot
- Maven
- Docker

## Project Creation Rules

Creating a new service:

Must define before generation:

- groupId
- artifactId
- packageName
- directory structure

Standard package:
