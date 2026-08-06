# Phase 9-A-1 Dashboard 基础实现计划


## 目标

实现最小可用 Dashboard。

范围：
- 系统状态展示
- 基础统计
- 健康检查展示


## 页面

Dashboard 首页：

展示：

- 服务状态
- PostgreSQL状态
- Agent数量
- Job总数
- Running数量
- Failed数量
- Recovery数量


## 后端

新增规划：

Controller：
- DashboardController

DTO：
- DashboardSummaryDTO


API：

GET /api/dashboard/summary


返回：

- health
- agents
- jobs
- executions
- recovery


## 前端

新增页面：

dashboard

组件：

- HealthCard
- JobSummaryCard
- ExecutionSummaryCard
- RecoveryCard


技术：

- Vue3
- Element Plus


## 数据来源

优先复用：

- Health API
- Job Repository
- Execution Repository
- Audit/Recovery数据


不修改：

- Job执行流程
- Scheduler
- Worker
- ExecutionEngine


## 测试

新增：

- DashboardControllerTest
- DashboardServiceTest


## 开发顺序

1. 后端summary API
2. 前端Dashboard页面
3. 数据绑定
4. 测试
