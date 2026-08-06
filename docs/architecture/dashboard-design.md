# AI Dev OS v1.1 Dashboard 设计文档


## 1. Web Dashboard 目标

- 将 Orchestrator 核心能力可视化
- 不改变现有执行逻辑


## 2. 页面设计


### Dashboard

显示：
- 系统健康状态
- Agent状态
- Job统计
- Execution统计
- Recovery状态


### Jobs

显示：
- jobId
- status
- priority
- lease
- retry
- 时间信息


### Execution

显示：
- executionId
- attempt
- status
- failure信息
- recovery信息


### Timeline

显示：
- PlanRun
- StepRun
- Job
- Execution
- Audit事件


### Agent

管理：
- Hermes
- Codex
- OpenClaw
- MCP

状态：
- online
- idle
- running
- error


## 3. API规划

整理已有能力：
- Health
- Job
- Execution
- Audit
- Timeline

规划新增：
- Dashboard summary
- Agent status
- Metrics


## 4. 前端方案

基于现有：
- Vue3
- Element Plus
- ECharts


## 5. 后端影响范围

说明可能涉及：
- Controller
- DTO
- Service

保持：
- Orchestrator核心流程不变


## 6. 开发拆分

- Phase 9-A-1 基础Dashboard
- Phase 9-A-2 Job/Execution监控
- Phase 9-A-3 Timeline
- Phase 9-A-4 Agent管理
