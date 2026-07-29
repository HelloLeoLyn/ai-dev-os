# AI Dev OS Agent Permission Policy

版本： v1.0

# 1. 目标

定义 Agent 执行任务时的权限边界。

目标：

- 减少无意义人工确认
- 保证危险操作可控
- 提高 Agent 执行效率

# 2. 权限等级

## Level 0：自动执行

特点：

只读或可重复操作。

允许：

- 查看文件
- 搜索代码
- 查看 Git 状态
- 查看 Git 历史
- 查看 Docker 状态
- 查看日志
- 执行测试

示例：

```bash
git status
git diff
git log
mvn test
docker ps
docker logs
```

---

## Level 1：一次确认

特点：

会产生环境变化，但风险可控。

需要确认：

- 创建文件
- 修改代码
- git add
- git commit
- docker build
- docker run
- 安装项目依赖

---

## Level 2：强制确认

特点：

可能造成不可逆影响。

必须确认：

- 删除文件
- 删除容器
- git reset
- git push
- 修改系统配置
- sudo 操作

---

# 3. 开发模式策略

Development Mode:

自动执行：

- 项目读取
- 项目分析
- 测试执行
- 构建执行
- 日志查看

需要确认：

- 文件修改
- Git 提交
- Docker 环境修改

禁止自动：

- 删除
- 推送
- 系统级修改

---

# 4. 执行流程

Agent 收到任务

↓

判断操作等级

↓

Level 0： 直接执行

↓

Level 1： 请求确认

↓

Level 2： 人工确认

↓

记录执行结果

---

# 5. 核心原则

Agent 拥有执行能力。

用户拥有最终控制权。

权限不是限制 Agent。

权限用于保证 Agent 行为可预测。
