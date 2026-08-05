# 02 WSL2 + Ubuntu 环境配置

本章完成 WSL2 与 Ubuntu 发行版安装，并配置 AI Dev OS
实际使用的用户、系统、网络与工作目录。

> 前提：已完成 `01-windows11.md` 中的虚拟化检查与功能开启。

---

## 1. 安装 WSL2

以管理员身份打开 PowerShell，执行：

```powershell
# 安装 WSL 并启用所需功能（自动下载默认发行版）
wsl --install

# 确认 WSL 版本
wsl --version

# 设置默认版本为 2
wsl --set-default-version 2
```

安装完成后重启电脑，再执行：

```powershell
wsl --status
```

预期：

- `默认版本：2`
- 已安装至少一个 Linux 发行版

---

## 2. Ubuntu 发行版选择

查看可安装的发行版：

```powershell
wsl --list --online
```

AI Dev OS 推荐：

- Ubuntu 24.04 LTS（首选）
- Ubuntu 22.04 LTS（兼容备选）

安装指定发行版：

```powershell
wsl --install Ubuntu-24.04
```

查看已安装发行版：

```powershell
wsl -l -v
```

注意：

- 每个发行版独立文件系统，可并存多个
- AI Dev OS 文档与脚本基于 Ubuntu LTS 验证

---

## 3. 初始化用户

首次进入 Ubuntu 时按提示完成：

- 设置用户名（如 `administrator`）
- 设置密码

```powershell
wsl -d Ubuntu-24.04
```

进入后确认当前用户并赋予 sudo 权限（首用户默认在 `sudo` 组）：

```bash
whoami
id
```

若需添加其他用户到 sudo 组：

```bash
sudo usermod -aG sudo <用户名>
```

---

## 4. 更新系统

```bash
sudo apt update
sudo apt upgrade -y
sudo apt autoremove -y
```

确认系统版本：

```bash
cat /etc/os-release
```

注意：

- 更新后如提示内核相关变更，建议 `wsl --shutdown` 后重启
- 中国大陆网络建议先配置镜像源（见第 7 节）再更新

---

## 5. 基础软件安装

AI Dev OS 搭建需要的基础工具：

```bash
sudo apt install -y \
  curl \
  wget \
  git \
  ca-certificates \
  gnupg \
  unzip \
  zip \
  build-essential \
  software-properties-common \
  locales \
  tzdata
```

时区与语言（AI Dev OS 环境使用 Asia/Shanghai）：

```bash
sudo timedatectl set-timezone Asia/Shanghai
sudo locale-gen en_US.UTF-8 zh_CN.UTF-8
```

验证：

```bash
curl --version
git --version
python3 --version
```

---

## 6. WSL 配置建议

### 6.1 Windows 侧：%UserProfile%\.wslconfig

限制 WSL2 资源占用（内存 8 GB、4 核，按机器实际调整）：

```ini
[wsl2]
memory=8GB
processors=4
swap=4GB
```

修改后生效：

```powershell
wsl --shutdown
```

### 6.2 Ubuntu 侧：/etc/wsl.conf

```ini
[boot]
systemd=true

[network]
generateResolvConf=true

[interop]
enabled=true
appendWindowsPath=true
```

说明：

- `systemd=true`：Docker 等以 systemd 管理服务的必需项
- `appendWindowsPath=true`：可在 WSL 内直接调用 Windows 程序（如 `code`）

修改后重启：

```powershell
wsl --shutdown
```

### 6.3 磁盘空间

WSL2 使用 VHD 虚拟磁盘，只增不减；回收空间：

```powershell
wsl --shutdown
diskpart
# select vhd file=<发行版磁盘路径>
# compact vdisk
```

---

## 7. 网络 / DNS 说明

默认网络模式：

- WSL2 使用 NAT 模式，通过虚拟网卡访问外部网络
- Windows 侧通过 `localhost` 即可访问 WSL2 内服务（端口转发自动生效）
- WSL2 内访问 Windows 宿主机使用 `localhost` 或 `127.0.0.1`

DNS 问题处理：

- 若 WSL 内无法解析域名，先检查 `/etc/resolv.conf` 是否正常生成
- 使用 `/etc/wsl.conf` 中 `generateResolvConf=true` 自动生成
- 中国大陆网络建议配置国内 DNS 或镜像源

apt 镜像源（可选，提升更新速度）：

```bash
sudo sed -i 's@//.*archive.ubuntu.com@//mirrors.aliyun.com@g; s@//security.ubuntu.com@//mirrors.aliyun.com@g' /etc/apt/sources.list.d/ubuntu.sources
sudo apt update
```

> 企业代理环境需在 WSL 内配置 `http_proxy` / `https_proxy` 环境变量，
> 后续章节（Codex / DeepSeek / npm / Maven）均会涉及网络访问。

---

## 8. 路径规划（workspace）

推荐目录结构：

```text
/home/<用户>/workspace/          # 开发工作区
└── ai-dev-os/                   # AI Dev OS 代码仓库
```

AI Dev OS 实际环境示例：

```text
/home/administrator/workspace/ai-dev-os
```

原则：

- 项目代码放在 WSL 文件系统内（`/home/...`），不要放在 `/mnt/c`（跨文件系统 IO 慢）
- Windows 侧访问 WSL 内文件：资源管理器地址栏输入 `\\wsl.localhost\Ubuntu-24.04\home\<用户>\workspace`
- WSL 内访问 Windows 文件：`/mnt/c/...`
- Maven 本地仓库与 npm 缓存默认位于 WSL 家目录，无需额外规划

克隆仓库：

```bash
mkdir -p ~/workspace
cd ~/workspace
git clone <AI-Dev-OS-仓库地址>
```

---

## 9. 验证命令

按顺序执行：

```bash
# 1. WSL 版本与发行版
wsl -l -v

# 2. 内核与系统版本
uname -a
cat /etc/os-release

# 3. 用户与权限
whoami
id

# 4. 基础工具
curl --version
git --version

# 5. 网络连通性
curl -I https://mirrors.aliyun.com

# 6. systemd 状态（需重启 WSL 后验证）
systemctl is-system-running

# 7. 磁盘与内存
df -h ~
free -h
```

预期结果：

- 发行版 `VERSION` 列为 `2`
- 系统为 Ubuntu 22.04/24.04 LTS
- 当前用户具备 sudo 权限
- `systemctl is-system-running` 返回 `running`（或 `degraded` 但可继续）

---

## 10. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| WSL 内无法联网 / DNS 解析失败 | resolv.conf 异常或网络代理 | 检查 `/etc/resolv.conf`，确认 `generateResolvConf=true`，重启 WSL |
| apt 更新极慢 | 默认官方源 | 切换阿里云等国内镜像源 |
| systemd 未运行 | WSL 版本过旧或未开启 | `wsl --update`，配置 `/etc/wsl.conf` 的 `systemd=true` |
| 时区不对 | 未设置 | `sudo timedatectl set-timezone Asia/Shanghai` |
| 中文显示乱码 | 缺少 locale | `sudo locale-gen zh_CN.UTF-8` 并设置 `LANG` |
| 在 `/mnt/c` 下开发很慢 | 跨文件系统 IO 开销大 | 项目放 WSL 文件系统内，`/mnt/c` 仅做数据交换 |
| WSL 虚拟磁盘越来越大 | VHD 只增不减 | `wsl --shutdown` 后用 diskpart compact |
| 端口无法从 Windows 访问 | 服务未启动或防火墙 | 确认服务监听 `0.0.0.0` 或使用 localhost 转发，检查 Windows 防火墙 |
| 默认进入用户不对 | 多用户混乱 | 在 `/etc/wsl.conf` 配置 `[user] default=<用户名>` |
| 无法调用 Windows 命令 | interop 关闭 | 确认 `/etc/wsl.conf` 中 `interop enabled=true` |
