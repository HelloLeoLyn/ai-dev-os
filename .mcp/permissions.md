# AI Dev OS MCP Permission Policy

## 1. 默认原则

所有 MCP Server 默认采用最小权限原则。

Agent 可以：

- 查询信息
- 读取文件
- 分析状态

Agent 不可以：

- 未授权修改数据
- 删除资源
- 修改系统配置

---

## 2. Filesystem MCP

### Allowed

默认允许：

- read_file
- read_text_file
- list_directory
- search_files

### Require Approval

需要人工确认：

- write_file
- edit_file
- move_file
- create_directory

### Forbidden Without Explicit Confirmation

禁止自动执行：

- delete file
- recursive delete
- modify system files

---

## 3. Git MCP

默认：

只读。

允许：

- git status
- git log
- git diff

需要确认：

- git commit
- git branch

危险：

- git reset
- git clean
- force push

---

## 4. Docker MCP

允许：

- 查看容器状态
- 查看日志

需要确认：

- 启动服务
- 停止服务
- 创建容器

危险：

- 删除容器
- 删除镜像
- 清理 volume

---

## 5. Browser MCP

允许：

- 打开页面
- 获取页面信息

需要确认：

- 登录
- 提交表单
- 上传文件

---

## 6. Human Approval

以下情况必须等待人工确认：

- 修改代码
- 修改配置
- 数据删除
- 发布部署
- 权限提升
