# AI Dev OS Execution Profile

版本： v1.0

# 1. 目标

Execution Profile 定义 Agent 在不同环境下的执行策略。

目标：

- 减少重复人工确认
- 提高 Agent 执行效率
- 保证危险操作受控

---

# 2. Profile 类型

## Development Profile

用于日常开发。

特点：

- 自动执行低风险操作
- 修改和提交需要确认
- 禁止危险操作自动执行

---

# 3. 权限映射

## Auto Execute

自动执行：

- 文件读取
- 项目分析
- Git 状态查看
- Git 差异查看
- Git 历史查看
- 测试执行
- 构建执行
- Docker 状态查看
- Docker 日志查看

示例：

```bash
git status
git diff
git log
mvn test
mvn package
docker ps
docker logs
```

---

## Require Confirm

需要确认：

- 创建文件
- 修改代码
- git add
- git commit
- docker build
- docker run
- 安装依赖

---

## Always Confirm

强制确认：

- 删除文件
- 删除容器
- git reset
- git push
- sudo 操作
- 修改系统配置

---

# 4. 执行流程

Agent 接收任务

↓

读取 Execution Profile

↓

判断操作风险等级

↓

自动执行或请求确认

↓

记录执行结果

---

# 5. 设计原则

权限控制不是限制 Agent。

目标：

让 Agent 在明确边界内自主工作。

低风险自动化。

高风险人工控制。
