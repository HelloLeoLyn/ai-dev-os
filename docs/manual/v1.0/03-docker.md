# 03 Docker 环境配置

本章配置 Docker 运行环境。AI Dev OS 使用 Docker 提供
PostgreSQL、测试容器（Testcontainers）与后续服务容器化能力。

> 前提：已完成 `02-wsl2-ubuntu.md`，WSL2 与 systemd 正常。

---

## 1. Docker 方案说明

### 1.1 Docker Desktop + WSL2（推荐）

- Windows 图形化安装，自带 WSL2 后端
- 自动与已安装的 WSL 发行版集成
- 适合日常开发，管理界面友好

### 1.2 Docker Engine（纯 WSL2 内运行）

- 直接在 Ubuntu 内安装 Docker Engine，不依赖 Docker Desktop
- 资源占用更小，更接近生产环境
- 需要 WSL2 的 systemd 支持（已在第二章开启）
- 镜像与容器仅存在于该发行版内，Windows 侧无图形界面

| 对比项 | Docker Desktop | Docker Engine |
| --- | --- | --- |
| 安装位置 | Windows + WSL2 集成 | WSL2 发行版内 |
| 管理界面 | 有 | 无（命令行） |
| systemd 依赖 | 无 | 需要 |
| 资源占用 | 较高 | 较低 |
| 适用场景 | 日常开发 | 生产模拟 / 最小化环境 |

AI Dev OS 开发推荐 Docker Desktop；部署验证可使用 Docker Engine。

---

## 2. Docker Desktop 安装

1. 下载 Docker Desktop for Windows
2. 安装时勾选「Use WSL 2 instead of Hyper-V」
3. 安装完成后重启电脑
4. 首次启动按引导完成（如接受许可）

验证 Windows 侧：

```powershell
docker version
```

---

## 3. WSL2 集成配置

1. 打开 Docker Desktop → Settings → Resources → WSL Integration
2. 勾选 AI Dev OS 使用的发行版（如 `Ubuntu-24.04`）
3. 点击 Apply & Restart

在 Ubuntu 内验证集成：

```bash
docker context ls
```

预期能看到默认 context，且 WSL 内可直接使用 `docker` 命令。

> 使用 Docker Engine 方案时跳过本节的 WSL Integration 设置，
> Docker 直接运行在发行版内部。

---

## 4. Docker 权限配置

将当前用户加入 `docker` 组，免 sudo 执行 docker：

```bash
sudo usermod -aG docker $USER
```

重新登录或重启 WSL 后生效：

```powershell
wsl --shutdown
```

验证权限：

```bash
docker ps
```

> 安全提示：`docker` 组等价于 root 权限，仅在可信开发环境使用。

---

## 5. 验证安装

```bash
# 版本信息
docker version

# 环境与存储驱动信息
docker info

# 拉取并运行测试镜像
docker run hello-world
```

预期结果：

- `docker version` 同时输出 Client 与 Server（Engine）版本
- `docker info` 显示 Storage Driver（如 `overlay2`）、镜像/容器数量
- `docker run hello-world` 输出 Hello from Docker! 确认消息

若 `docker version` 的 Server 段为空或报错：

- Docker Desktop 未启动：启动后再试
- WSL 集成未开启：回到第 3 节配置
- 权限不足：执行第 4 节

---

## 6. AI Dev OS 使用场景

### 6.1 PostgreSQL

AI Dev OS 后端连接 PostgreSQL（默认 `localhost:5432`），可用容器启动：

```bash
docker run -d \
  --name ai-dev-os-postgres \
  -e POSTGRES_DB=ai_dev_os \
  -e POSTGRES_USER=ai_dev_os \
  -e POSTGRES_PASSWORD=<密码> \
  -p 5432:5432 \
  -v ai-dev-os-pgdata:/var/lib/postgresql/data \
  postgres:16
```

> 详细建库与连接配置见 `05-postgresql.md`。

### 6.2 Testcontainers

`services/orchestrator` 的测试使用 Testcontainers PostgreSQL：

- 测试时自动启动临时 PostgreSQL 容器
- 需要 Docker 可用（测试进程能访问 docker daemon）
- 运行测试前确认 `docker ps` 正常

```bash
cd services/orchestrator
./mvnw test
```

### 6.3 后续服务容器化

- 仓库根目录 `docker/` 目前为空，容器化编排待落地
- `services/orchestrator/Dockerfile` 已存在，可构建后端镜像
- 规划中的 docker-compose 将编排 orchestrator + PostgreSQL 等服务

```bash
# 构建后端镜像（示例）
cd services/orchestrator
docker build -t ai-dev-os/orchestrator:0.0.1-SNAPSHOT .
```

---

## 7. 镜像拉取问题

### 7.1 网络问题

- 拉取镜像超时：多为网络到 Docker Hub 不稳定
- 重试或改用加速镜像源

### 7.2 镜像源配置

Docker Desktop：Settings → Docker Engine 中添加 registry-mirrors：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io"
  ]
}
```

Docker Engine：编辑 `/etc/docker/daemon.json`：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io"
  ]
}
```

然后重启 Docker：

```bash
sudo systemctl restart docker
```

### 7.3 代理说明

企业网络需要代理时：

```bash
# /etc/systemd/system/docker.service.d/http-proxy.conf
[Service]
Environment="HTTP_PROXY=http://<代理地址>:<端口>"
Environment="HTTPS_PROXY=http://<代理地址>:<端口>"
Environment="NO_PROXY=localhost,127.0.0.1"
```

重启生效：

```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

> 代理地址需填 WSL 内可访问的地址；若代理在 Windows 侧，需使用
> 宿主机 IP（而非 `localhost`），并确认代理监听对应端口。

---

## 8. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `docker version` Server 段为空 | Docker Desktop 未启动或集成未开 | 启动 Docker Desktop，检查 WSL Integration |
| `permission denied ... /var/run/docker.sock` | 用户不在 docker 组 | `sudo usermod -aG docker $USER` 后重启 WSL |
| `Cannot connect to the Docker daemon` | daemon 未运行 | Docker Engine 方案执行 `sudo systemctl start docker` |
| 拉取镜像超时 | 网络到 Docker Hub 不稳定 | 配置 registry-mirrors 或代理后重试 |
| 镜像源不生效 | daemon.json 未重启 | 修改后 `sudo systemctl restart docker` |
| Testcontainers 测试失败 | 测试进程无法访问 docker | 确认 `docker ps` 正常，当前用户在 docker 组 |
| 容器端口与本地冲突 | 5432 已被占用 | 检查 `ss -ltnp`，停止占用进程或改映射端口 |
| WSL 内 docker 与 Windows 不一致 | 多 context 混乱 | `docker context ls` 检查，切换到目标 context |
