# Python / Security Tooling Lessons

## LESSON-TOL-001 尊重 Ubuntu PEP 668 边界

Category: Tooling
Maturity: LESSON

Context: Ubuntu system Python 可标记为 externally managed。
Symptom: `python3 -m pip install --user` 返回 externally-managed-environment。
Root Cause: PEP 668 防止 pip 修改发行版管理的 Python environment。
Diagnosis: 检查 interpreter、EXTERNALLY-MANAGED marker 与工具官方安装方式。
Temporary Fix: 使用 venv 或 pipx。
Permanent Fix: CLI 工具采用隔离环境或官方 binary。
Lesson Learned: 系统 Python 是操作系统资产，不是通用 CLI 安装目录。
Engineering Rule: 禁止用 `--break-system-packages` 绕过边界。
Automated Guard: None.
Evidence: HISTORICAL；仓库不管理系统 Python。
Related: LESSON-TOL-002.
Next Improvement: 在安装文档中列出受信安装渠道。

## LESSON-TOL-002 Security CLI 安装必须隔离且来源可信

Category: Tooling / Security
Maturity: LESSON

Context: Semgrep、Gitleaks、Trivy 属于 runtime tooling，不属于应用依赖。
Symptom: 为安装 scanner 污染 system Python 或引入不可追踪 binary。
Root Cause: 未区分应用依赖、系统 package 与独立 CLI lifecycle。
Diagnosis: 核对 binary path、version、package owner 与来源。
Temporary Fix: 在 venv/pipx 中安装 Python CLI。
Permanent Fix: Semgrep 使用 venv/pipx；Gitleaks 使用 official binary；Trivy 使用 official binary 或 trusted repository。
Lesson Learned: 安全工具的供应链也属于安全边界。
Engineering Rule: scanner 安装不得污染 system Python，且不得自动 sudo 安装。
Automated Guard: None；availability detection 只检测，不负责安装。
Evidence: `6b6f783`; `SecurityScannerAvailability.java`.
Related: [Security Rules](../../engineering-rules/security-rules.md).
Next Improvement: 为 scanner binary provenance 增加可选验证。
