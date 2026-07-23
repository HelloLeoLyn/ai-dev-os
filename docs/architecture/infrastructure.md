# AI Dev OS Infrastructure

## 系统基础

运行环境：

- Windows 11
- WSL2 Ubuntu
- Docker Desktop

## 目录职责

docker/

负责：

- 容器管理
- 服务编排

services/

负责：

- AI Agent 服务

scripts/

负责：

- 自动化启动
- 环境初始化

configs/

负责：

- Agent配置
- 服务配置

## 设计原则

1. 环境隔离

2. 配置集中管理

3. 可重复部署

4. 可恢复
