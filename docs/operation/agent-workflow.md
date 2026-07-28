# 6. MCP调用流程

Agent 不直接操作外部资源。

所有外部能力必须通过 MCP。

流程：

Agent分析需求

↓

选择对应 MCP

↓

检查权限

↓

执行操作

↓

返回结果

↓

Agent继续决策

---

# 7. MCP能力分工

## Filesystem MCP

用途：

项目文件访问。

能力：

- 读取文件
- 搜索代码
- 修改文件

权限：

默认读取。

修改需要确认。

---

## Git MCP

用途：

版本管理。

能力：

- 查看状态
- 查看历史
- 提交代码
- 分支管理

原则：

重要修改必须留下 Git 记录。

---

## Docker MCP

用途：

开发环境管理。

能力：

- 查看容器
- 查看日志
- 管理服务

权限：

环境修改需要确认。

---

## Browser Automation

用途：

Web自动化测试。

能力：

- 页面访问
- 点击
- 输入
- 截图
- 页面验证

权限：

涉及登录、提交、数据修改需要确认。

---

# 8. 完整开发流程

需求输入

↓

Planner Agent 分析

↓

Filesystem MCP 获取项目上下文

↓

Developer Agent 修改代码

↓

Git MCP 保存版本

↓

Docker MCP 启动环境

↓

Tester Agent 执行测试

↓

Browser Automation 验证页面

↓

Git MCP 提交结果

↓

生成任务报告
