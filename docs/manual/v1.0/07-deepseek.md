# 07 DeepSeek 模型配置与切换

本章配置 DeepSeek 模型作为 OpenAI 模型的补充或替代，
并说明与 Codex CLI 的集成方式。

> 前提：已完成 `06-codex-cli.md`，Codex CLI 可用。

---

## 1. DeepSeek 在 AI Dev OS 中的作用

### 1.1 为什么替换 / 补充 OpenAI 模型

- 成本控制：DeepSeek API 价格更低
- 网络可达性：部分地区访问 OpenAI 受限，DeepSeek 更稳定
- 推理能力：`deepseek-reasoner` 适合复杂任务分析

### 1.2 与 Codex 的关系

- Codex CLI 通过自定义 `model_provider` 接入 DeepSeek
- DeepSeek 提供 OpenAI 兼容接口，无需修改 Codex 主体逻辑
- 通过 profile 在 OpenAI / DeepSeek 之间切换

---

## 2. API Key 配置

DeepSeek 平台申请 API Key 后，配置环境变量。

推荐独立变量：

```bash
export DEEPSEEK_API_KEY='sk-xxxxxxxx'
```

也可复用 `OPENAI_API_KEY`（通过 `env_key` 指向）：

```bash
export OPENAI_API_KEY='sk-xxxxxxxx'
```

环境变量安全管理：

- 写入 `~/.bashrc` 或使用 direnv（项目级 `.envrc`）
- 敏感值用 `read -s` 输入，避免进入 shell history
- 不提交到 Git 仓库，加入 `.gitignore`
- 多 Key 场景按 profile 隔离，避免混淆

---

## 3. Codex model_provider 配置

在 `~/.codex/config.toml` 中声明 provider：

```toml
[model_providers.deepseek]
name = "DeepSeek"
base_url = "https://api.deepseek.com"
env_key = "DEEPSEEK_API_KEY"
wire_api = "chat"
```

字段说明：

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `name` | `DeepSeek` | 显示名称 |
| `base_url` | `https://api.deepseek.com` | OpenAI 兼容接口地址 |
| `env_key` | `DEEPSEEK_API_KEY` | API Key 对应环境变量 |
| `wire_api` | `chat` | DeepSeek 兼容 chat completions |

> 若使用 `OPENAI_API_KEY`，将 `env_key` 改为 `OPENAI_API_KEY`。

---

## 4. profile 配置

在 `~/.codex/config.toml` 中定义 profile：

```toml
[profiles.deepseek]
model = "deepseek-chat"
model_provider = "deepseek"
```

字段说明：

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `model` | `deepseek-chat` / `deepseek-reasoner` | DeepSeek 模型名 |
| `model_provider` | `deepseek` | 对应第 3 节的 provider 名称 |

切换使用：

```bash
codex --profile deepseek
```

---

## 5. config.toml 与 deepseek.config.toml 关系

### 5.1 两种组织方式

方式一：单一 `config.toml` + profile

- `~/.codex/config.toml` 中定义 `[model_providers.deepseek]`
  与 `[profiles.deepseek]`
- 通过 `codex --profile deepseek` 切换

方式二：独立配置文件

- 创建 `~/.codex/deepseek.config.toml`，只放 DeepSeek 相关配置
- 通过 `--config` 显式加载：

```bash
codex --config ~/.codex/deepseek.config.toml
```

### 5.2 使用建议

- 日常多模型切换：推荐方式一（profile）
- 配置隔离清晰：推荐方式二（独立文件）
- 两种方式不要混用同一 profile 名，避免冲突

### 5.3 常见配置错误

- 同一 profile 在多处重复定义（config.toml 与独立文件各一份）
- profile 只写了 `model`，漏写 `model_provider`
- `model_provider` 名称与 `[model_providers.xxx]` 名称不一致
- `env_key` 指向的环境变量未导出
- `base_url` 拼写错误或缺少协议头

---

## 6. 模型切换验证

```bash
# 以 deepseek profile 启动
codex --profile deepseek
```

启动后在交互界面：

```text
/model
```

预期：显示当前使用的模型（如 `deepseek-chat`），可查看/切换。

简单测试任务：

```text
输出 1+1=2 并说明你当前使用的模型
```

预期：正常返回结果，且能正确报告当前模型。

验证不通过时检查：

- 环境变量是否导出（`echo $DEEPSEEK_API_KEY`）
- provider 与 profile 配置是否正确
- `base_url` 是否可达（`curl https://api.deepseek.com`）

---

## 7. 当前实际踩坑记录

### 7.1 legacy profile 冲突

- 现象：配置了 profile 后启动仍使用旧模型，或报配置冲突
- 原因：旧版本遗留的 `[profiles.deepseek]` 与新配置文件重复定义
- 处理：统一在一处定义，删除重复项，确认无旧 `deepseek.config.toml` 残留

### 7.2 模型 metadata warning

- 现象：启动时出现模型元数据相关 warning
- 原因：DeepSeek 返回的模型信息与 OpenAI 格式存在差异
- 处理：属可忽略 warning，不影响任务执行；如影响识别，核对模型名

### 7.3 API 兼容问题

- 现象：某些请求报错或参数不支持
- 原因：DeepSeek 兼容 chat completions，但部分高级特性
  （如原生 responses API、部分推理参数）不支持
- 处理：`wire_api` 使用 `chat`；避免使用 DeepSeek 不支持的参数

---

## 8. 常见问题

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 401 Unauthorized | API Key 无效或未导出 | 核对 `DEEPSEEK_API_KEY`，确认 `env_key` 一致 |
| 模型名不存在 | 模型名拼写错误 | 使用 `deepseek-chat` / `deepseek-reasoner` |
| 启动仍是 OpenAI 模型 | profile 未生效 | 确认 `--profile deepseek` 与 `[profiles.deepseek]` 配置 |
| 配置冲突 | 多处定义同一 profile | 统一配置位置，删除重复 |
| 请求参数报错 | 使用了不兼容参数 | `wire_api` 设为 `chat`，去掉不支持的参数 |
| warning 干扰 | DeepSeek 元数据差异 | 可忽略，不影响使用 |
| API 请求超时 | 网络问题 | 检查网络/代理，`curl https://api.deepseek.com` 测试 |
| 误用 OpenAI Key | env_key 指向错误 | 确认使用独立 `DEEPSEEK_API_KEY` 或显式指定 |
