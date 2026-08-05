# 11 服务启动与停止

本章说明 AI Dev OS 前后端服务的启动、状态检查、停止与重启流程。

> 前提：已完成 `10-deploy.md`，代码已克隆并构建。

---

## 1. 启动前检查

### 1.1 WSL2

```bash
wsl -l -v
systemctl is-system-running
```

预期：发行版为 WSL2，systemd 正常。

### 1.2 Docker（按需）

```bash
docker ps
```

- 使用 PostgreSQL 容器时确认容器运行
- Testcontainers 测试需要 docker daemon 可用

### 1.3 PostgreSQL

```bash
PGPASSWORD='<强密码>' psql -h localhost -p 5432 -U ai_dev_os -d ai_dev_os -c "SELECT 1;"
```

预期：返回 `1`。

### 1.4 环境变量

```bash
export AI_DEV_OS_PERSISTENCE_TYPE=postgresql
export AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://localhost:5432/ai_dev_os
export AI_DEV_OS_POSTGRES_USER=ai_dev_os
export AI_DEV_OS_POSTGRES_PASSWORD='<强密码>'
export OPENCLAW_GATEWAY_TOKEN='<强随机Token>'
```

> 开发环境可保持 `in-memory` 模式并跳过 PostgreSQL；
> 生产环境必须使用 `postgresql`。

---

## 2. 一键启动

```bash
services/orchestrator/scripts/start-all.sh
```

执行流程：

1. 检查 `java` 与 `mvnw` 可用
2. 后台启动后端（`spring-boot:run`，端口 `18080`）
3. 检查 `node` / `npm` 与 `node_modules`
4. 后台启动前端（Vite dev server，端口 `15174`）
5. 输出访问地址：

```text
Frontend: http://127.0.0.1:15174
Backend:  http://127.0.0.1:18080
```

停止：

- 按 `Ctrl+C`，脚本会同时停止前后端
- 任一服务异常退出时，脚本停止另一个服务

---

## 3. 后端启动

### 3.1 Maven 启动方式

```bash
cd services/orchestrator
./mvnw spring-boot:run \
  --server.port=18080 \
  --spring.profiles.active=local
```

### 3.2 jar 启动方式

先构建：

```bash
./mvnw clean package -DskipTests
```

再运行：

```bash
java -jar target/orchestrator-0.0.1-SNAPSHOT.jar
```

### 3.3 环境变量加载

- 启动前在当前 shell 中 `export`（见第 1.4 节）
- 或写入 `~/.bashrc` / 使用 direnv 自动加载
- `start-backend.sh` 会自动设置 `OPENCLAW_GATEWAY_URL` 默认值

---

## 4. 前端启动

### 4.1 安装依赖

```bash
cd services/orchestrator/frontend
npm install
```

### 4.2 开发模式

```bash
npm run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

- API 请求由 Vite 代理到 `http://127.0.0.1:18080`
- `--strictPort` 端口被占用时直接报错

### 4.3 构建后部署方式

```bash
npm run build
```

产物 `frontend/dist/` 的部署方式：

- 由 Nginx / Caddy 等静态服务器托管
- `/api` 路径反向代理到后端 `http://127.0.0.1:18080`
- 或由 Spring Boot 静态资源托管（按项目实现）

---

## 5. 服务状态检查

### 5.1 后端端口 18080

```bash
curl -s http://127.0.0.1:18080/api/health
curl -s http://127.0.0.1:18080/api/health/readiness
ss -ltnp | grep 18080
```

预期：

- `/api/health`：`200` `{"status":"UP"}`
- readiness：`200 READY`（未就绪 `503 NOT_READY`）
- `ss` 显示后端进程监听 `18080`

### 5.2 前端端口 15174

```bash
curl -s -I http://127.0.0.1:15174
ss -ltnp | grep 15174
```

预期：HTTP `200`，Vite 进程监听 `15174`。

### 5.3 PostgreSQL 连接

```bash
PGPASSWORD='<强密码>' psql -h localhost -U ai_dev_os -d ai_dev_os \
  -c "SELECT version, success FROM schema_migrations ORDER BY version;"
```

预期：V1～V7 记录且 `success` 均为 `true`。

---

## 6. 停止服务

### 6.1 优雅停止

一键启动场景：

- 按 `Ctrl+C`，`start-all.sh` 通过 trap 停止前后端

单独运行场景：

- 后端：`Ctrl+C`（`spring-boot:run` 进程）
- 前端：`Ctrl+C`（Vite 进程）

### 6.2 清理进程

若前台方式无法停止或残留进程：

```bash
# 查看相关进程
ps aux | grep -E "orchestrator|vite|spring-boot"

# 按 PID 停止（示例，谨慎使用）
kill <PID>
```

> 残留进程可使用 `pkill -f` 清理，但先确认目标进程，
> 避免误杀其他 Java/Node 服务。

---

## 7. 重启流程

标准重启顺序：

```text
1. 停止服务（第 6 节）
2. 确认端口释放（ss -ltnp）
3. 确认 PostgreSQL / Docker 正常（第 1 节）
4. 重新加载环境变量（第 1.4 节）
5. 启动服务（第 2～4 节）
6. 状态检查（第 5 节）
```

配置修改后的重启：

- 环境变量：重启后端进程生效
- 前端代码：Vite dev server 热更新，无需重启
- 后端代码：重新构建或 `spring-boot:run` 重启

---

## 8. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 端口占用（18080/15174） | 已有进程监听 | `ss -ltnp` 定位并停止，或改端口 |
| `--strictPort` 报错 | 15174 被占用 | 释放端口，前端仅用 15174 |
| 后端启动失败 | JDK 版本不对或依赖缺失 | 确认 Java 21 与 `mvnw` 可执行 |
| 后端启动即退出 | 数据库连接失败 | 检查 PostgreSQL 与 `AI_DEV_OS_POSTGRES_*` |
| 前端无法访问 | 未启动或端口错误 | 确认 Vite 监听 15174，`npm run dev` 正常 |
| 前端 API 请求失败 | 后端未启动或代理错误 | 确认后端 18080 存活，代理目标正确 |
| readiness 503 | 迁移未完成或数据库异常 | 查看日志，检查 `schema_migrations` |
| 一键启动部分失败 | 前后端其一异常 | 分别手动启动定位失败组件 |
