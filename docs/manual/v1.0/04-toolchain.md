# 04 Java + Maven + Node + Git + VS Code 工具链配置

本章完成 AI Dev OS 后端与前端构建所需的工具链安装与验证。

> 前提：已完成 `02-wsl2-ubuntu.md` 与 `03-docker.md`。

---

## 1. Java 21

### 1.1 安装方式

方式一：Ubuntu 官方 OpenJDK 21

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
```

方式二：Eclipse Temurin 21（推荐，与 CI/生产更一致）

```bash
sudo apt install -y wget apt-transport-https
sudo mkdir -p /etc/apt/keyrings
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /etc/apt/keyrings/adoptium.asc
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-21-jdk
```

### 1.2 JAVA_HOME 配置

确认 JDK 路径：

```bash
readlink -f $(which java)
```

示例路径：

- Temurin：`/usr/lib/jvm/temurin-21-jdk-amd64`
- OpenJDK：`/usr/lib/jvm/java-21-openjdk-amd64`

写入 `~/.bashrc`：

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

生效：

```bash
source ~/.bashrc
```

### 1.3 验证

```bash
java -version
echo $JAVA_HOME
```

预期：输出 Java 21 版本（如 `openjdk version "21.x"`），
`JAVA_HOME` 指向第 1.2 节配置的路径。

---

## 2. Maven

### 2.1 安装

Ubuntu 24.04 官方源为 Maven 3.8，AI Dev OS 使用 3.9.x，推荐手动安装：

```bash
cd /opt
sudo wget https://dlcdn.apache.org/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz
sudo tar -xzf apache-maven-3.9.11-bin.tar.gz
sudo mv apache-maven-3.9.11 maven
```

写入 `~/.bashrc`：

```bash
export MAVEN_HOME=/opt/maven
export PATH=$MAVEN_HOME/bin:$PATH
```

生效：

```bash
source ~/.bashrc
```

> 若使用 `services/orchestrator/mvnw` 包装脚本，可跳过 Maven 手动安装；
> 但建议仍安装以支持全局构建命令。

### 2.2 验证

```bash
mvn -version
```

预期：输出 Maven 3.9.x 与默认 JDK 信息。

### 2.3 Maven 仓库配置建议

本地仓库默认在 `~/.m2/repository`。

中国大陆网络建议配置阿里云镜像，编辑 `~/.m2/settings.xml`：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <url>https://maven.aliyun.com/repository/central</url>
    </mirror>
  </mirrors>
</settings>
```

企业代理环境可在 `settings.xml` 中配置 `<proxies>`。

---

## 3. Node.js

### 3.1 Node 版本要求

AI Dev OS 前端基于 Vue 3 + Vite，当前环境使用：

- Node 24.x（LTS 或 Current）
- npm 11.x

推荐使用 nvm 管理版本：

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.bashrc
nvm install 24
nvm use 24
nvm alias default 24
```

### 3.2 npm 配置

中国大陆网络建议使用国内 registry：

```bash
npm config set registry https://registry.npmmirror.com
```

查看配置：

```bash
npm config get registry
```

### 3.3 验证

```bash
node -v
npm -v
```

预期：`node -v` 输出 `v24.x`，`npm -v` 输出 `11.x`。

---

## 4. Git

### 4.1 安装

```bash
sudo apt install -y git
```

### 4.2 配置用户信息

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

可选：提交信息编辑默认中文环境时设置 UTF-8：

```bash
git config --global core.quotepath false
```

### 4.3 验证

```bash
git --version
git config --global --list
git status
```

预期：

- `git --version` 输出 Git 2.x
- `git config --global --list` 显示已配置的 name/email
- 在仓库目录执行 `git status` 正常显示工作区状态

---

## 5. VS Code + Remote WSL

### 5.1 推荐安装方式

1. Windows 侧安装 VS Code
2. 安装扩展：`WSL`（Microsoft 官方 Remote - WSL）
3. 无需在 WSL 内单独安装 VS Code

```powershell
# 打开 WSL 发行版
wsl -d Ubuntu-24.04

# 在 WSL 内打开 VS Code 并自动安装 server 组件
code ~/workspace/ai-dev-os
```

### 5.2 打开 WSL 项目目录

```bash
cd ~/workspace/ai-dev-os
code .
```

VS Code 左下角显示 `WSL: Ubuntu-24.04` 即表示已连接 WSL。

### 5.3 常用插件建议

| 扩展 | 用途 |
| --- | --- |
| WSL | Remote - WSL 连接 |
| Java Extension Pack | Java 开发（含 Maven 支持） |
| Vite / Vue 官方扩展 | Vue 前端开发 |
| EditorConfig / Prettier | 代码风格 |
| GitLens | Git 可视化 |
| Docker | Docker 容器管理 |

> Java 插件首次打开项目会下载语言服务，需保持网络可用。

---

## 6. AI Dev OS 当前环境版本矩阵

| 组件 | 版本 | 验证命令 |
| --- | --- | --- |
| Java | 21 | `java -version` |
| Maven | 3.9.x | `mvn -version` |
| Node.js | 24.x | `node -v` |
| npm | 11.x | `npm -v` |
| Git | 2.x | `git --version` |
| Docker | 最新稳定版 | `docker version` |
| Ubuntu | 22.04 / 24.04 LTS | `cat /etc/os-release` |
| WSL | 2.x | `wsl --version` |

> 版本矩阵为搭建基线；实际以 `services/orchestrator/pom.xml`
> 中 `java.version=21` 与 `frontend/package.json` 声明的依赖为准。

---

## 7. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| `java -version` 显示旧版本 | PATH 指向其他 JDK | 确认 `JAVA_HOME` 与 `PATH` 顺序，重新 `source ~/.bashrc` |
| `mvn` 命令找不到 | Maven 未加入 PATH | 检查 `MAVEN_HOME` 配置并重新加载 shell |
| Maven 依赖下载慢 | 官方中央仓库 | 配置阿里云 mirror |
| npm 安装超时 | 网络问题 | 配置 npmmirror registry 后重试 |
| `node -v` 版本不对 | 多个 Node 共存 | 用 nvm 切换并设置 default |
| `code` 命令不存在 | VS Code server 未安装 | Windows 侧确认 VS Code 与 WSL 扩展已装，重试 `code .` |
| VS Code 无法连接 WSL | WSL 版本过旧 | `wsl --update` 后重启 |
| Git 提交显示中文文件名乱码 | 未设置 UTF-8 | `git config --global core.quotepath false` |
