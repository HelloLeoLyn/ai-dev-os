# Git Authentication Lessons

## LESSON-GIT-001 长期 WSL 项目应避免重复 HTTPS 认证

Category: Git
Maturity: LESSON

Context: 长期 WSL development 需要稳定 push authentication。
Symptom: HTTPS credential 未持久化时重复要求认证。
Root Cause: remote 使用 HTTPS，但 runtime 没有合适的持久 credential helper。
Diagnosis: 核对 remote scheme、credential helper 与实际执行用户。
Temporary Fix: 使用受控 credential helper；不得把 credential 写入仓库。
Permanent Fix: 项目独立 SSH key、SSH config alias 与 SSH remote。
Lesson Learned: 自动化运行需要非交互、可撤销且不入库的认证方式。
Engineering Rule: 长期 automated project 优先使用 project-scoped SSH identity。
Automated Guard: None.
Evidence: HISTORICAL；仓库未实现 credential provisioning。
Related: [Git Rules](../../engineering-rules/git-rules.md).
Next Improvement: 编写不读取 private key 的 remote readiness probe。

## LESSON-GIT-002 不同项目可使用独立 SSH Identity

Category: Git / Security
Maturity: LESSON

Context: 不同项目可能属于不同权限域。
Symptom: 共用 identity 导致权限、撤销和审计耦合。
Root Cause: SSH host 与 key selection 未按项目隔离。
Diagnosis: 检查 SSH config alias、remote host 与 selected identity。
Temporary Fix: 手工通过 host alias 选择 key。
Permanent Fix: 为项目配置独立 alias 和 identity file；真实名称不写入本文档。
Lesson Learned: credential scope 应与项目授权边界一致。
Engineering Rule: 推荐 project-scoped SSH identity，禁止在仓库保存 private key。
Automated Guard: None.
Evidence: HISTORICAL / operational practice.
Related: LESSON-GIT-001.
Next Improvement: 项目 metadata 只保存 identity reference，不保存 secret material。
