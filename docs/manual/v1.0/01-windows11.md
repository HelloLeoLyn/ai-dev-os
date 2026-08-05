# 01 Windows 11 环境准备

本章完成 AI Dev OS 搭建前的宿主机准备，包括系统要求检查、
虚拟化确认与 WSL2 前置功能开启。

> 后续章节均假设本章验证通过。

---

## 1. 系统要求

| 项目 | 要求 | 说明 |
| --- | --- | --- |
| 操作系统 | Windows 11 22H2 及以上 | 推荐保持最新补丁 |
| 架构 | x64（Intel / AMD）或 ARM64 | AI Dev OS 以 x64 为主要验证平台 |
| 内存 | 16 GB 及以上 | WSL2 + Docker + Java 构建建议 16 GB 起 |
| 磁盘 | 至少 50 GB 可用空间 | WSL2 虚拟磁盘、Docker 镜像、Maven 仓库占用较大 |
| 管理员权限 | 需要 | 开启功能与安装 WSL 需要管理员 |
| CPU 虚拟化 | 必须开启 | Intel VT-x / AMD-V（SVM），见第 2 节 |

注意：

- Windows 11 家庭版支持 WSL2，无需额外授权
- 推荐使用 SSD，WSL2 与 Docker 对磁盘 IO 敏感

---

## 2. 开启虚拟化检查

WSL2 依赖 CPU 虚拟化能力，需先在固件（BIOS / UEFI）中开启：

- Intel CPU：`Intel Virtualization Technology (VT-x)`
- AMD CPU：`SVM Mode` 或 `AMD-V`

检查是否已开启：

```powershell
# 方式一：PowerShell 查询 CPU 虚拟化固件状态
Get-CimInstance Win32_Processor | Select-Object Name, VirtualizationFirmwareEnabled

# 方式二：systeminfo
systeminfo | findstr /C:"Hyper-V"
```

预期结果：

- `VirtualizationFirmwareEnabled` 为 `True`
- `systeminfo` 中显示 `已检测到虚拟机监控程序`，或虚拟化相关状态为 `是`

如果未开启：

1. 重启电脑，进入 BIOS / UEFI（通常按 `Del` / `F2` / `F10`）
2. 找到 CPU 虚拟化选项并设为 `Enabled`
3. 保存退出，重新进入 Windows 后再次检查

> 注意：在虚拟机中安装 Windows 时，需在宿主虚拟机设置中
> 开启「嵌套虚拟化」，否则 WSL2 无法运行。

---

## 3. WSL2 前置条件

WSL2 需要以下 Windows 功能：

- 适用于 Linux 的 Windows 子系统（`Microsoft-Windows-Subsystem-Linux`）
- 虚拟机平台（`VirtualMachinePlatform`）

确认方式：

```powershell
# 查看 WSL 状态与默认版本
wsl --status
wsl --version

# 查看已安装的发行版及各自版本
wsl -l -v
```

常见状态：

- `默认版本：2`：已配置 WSL2
- `默认版本：1`：需执行 `wsl --set-default-version 2`
- 命令不存在：WSL 未安装，先完成第 4 节功能开启

---

## 4. Windows 功能开启

### 4.1 推荐方式：wsl --install

以管理员身份打开 PowerShell 或 CMD，执行：

```powershell
wsl --install
```

该命令会自动：

- 启用 WSL 与虚拟机平台功能
- 下载并安装默认 Ubuntu 发行版
- 首次安装完成后需要重启电脑

### 4.2 手动方式：启用 Windows 功能

若 `wsl --install` 不可用，可手动开启功能：

```powershell
# 以管理员身份运行
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```

或在「控制面板 → 程序和功能 → 启用或关闭 Windows 功能」中勾选：

- 适用于 Linux 的 Windows 子系统
- 虚拟机平台

开启后重启电脑。

### 4.3 设置 WSL 默认版本为 2

```powershell
wsl --set-default-version 2
```

将现有发行版转换为 WSL2：

```powershell
wsl --set-version <发行版名称> 2
# 示例
wsl --set-version Ubuntu 2
```

---

## 5. 验证命令

完成上述步骤后，按顺序执行以下验证：

```powershell
# 1. 虚拟化已开启
Get-CimInstance Win32_Processor | Select-Object VirtualizationFirmwareEnabled

# 2. WSL 已安装且为版本 2
wsl --status
wsl --version

# 3. 默认发行版存在且为 WSL2
wsl -l -v

# 4. 进入 Ubuntu 并确认可执行命令
wsl
uname -a
```

预期结果：

- `VirtualizationFirmwareEnabled` 为 `True`
- `wsl --status` 显示默认版本为 2
- `wsl -l -v` 中 Ubuntu 的 `VERSION` 列为 `2`
- `uname -a` 正常输出 Linux 内核信息

---

## 6. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `VirtualizationFirmwareEnabled` 为 `False` | BIOS 未开启虚拟化 | 进入 BIOS 开启 VT-x / SVM，或开启嵌套虚拟化 |
| `wsl` 命令不存在 | WSL 未安装 | 执行 `wsl --install` 或手动启用功能后重启 |
| 发行版 `VERSION` 为 1 | 未设置默认版本 2 | `wsl --set-default-version 2` 后转换发行版 |
| 安装 WSL 提示需要管理员权限 | 功能开启需要提权 | 以管理员身份重新打开 PowerShell / CMD |
| 开启功能后长时间无响应 | 需要重启完成功能生效 | 重启电脑后重试 |
| 公司电脑受组策略限制 | 企业策略禁用虚拟化/WSL | 联系 IT 管理员，或改用已开启的专用开发机 |
| WSL2 占用内存过高 | 默认使用宿主机可用内存的 50% | 创建 `%UserProfile%\.wslconfig` 限制内存 |
| 在虚拟机内使用 WSL2 失败 | 未开启嵌套虚拟化 | 在宿主虚拟机设置中开启嵌套虚拟化 |
