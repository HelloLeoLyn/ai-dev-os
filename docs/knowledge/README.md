# AI Dev OS Engineering Knowledge

本知识库把真实工程事件沉淀为可复用约束，使问题从现象逐步升级为规则和自动防护。它不替代 [Troubleshooting](../troubleshooting/common-errors.md)：Troubleshooting 回答“现在如何恢复”，Lesson 回答“为何发生、以后如何避免”，Engineering Rule 定义“设计和实现必须遵守什么”。

## 成熟度

| Maturity | 含义 |
| --- | --- |
| `OBSERVED` | 已观察到现象，根因尚未由仓库或可重复测试充分确认。 |
| `CONFIRMED` | 根因已由真实环境或测试确认。 |
| `LESSON` | 已形成可复用经验，但尚未成为强制规则。 |
| `ENGINEERING_RULE` | 已形成明确工程规则，但尚无完整自动防护。 |
| `AUTOMATED_GUARD` | 已由代码、测试、Policy、Gate 或自动诊断强制执行。 |

成熟度描述的是当前最高阶段；证据不足时不得向上推断。

## 新增与升级流程

1. 使用下列模板记录可复现事实，并为 `OBSERVED` 与已确认事实划清边界。
2. 引用真实代码、测试、commit 或文档；没有 commit 时引用相关实现，不补造 hash。
3. 当经验能跨场景复用时升级为 `LESSON`；形成明确设计约束后写入 Engineering Rules。
4. 只有存在可定位的代码、测试、Policy、Gate 或诊断时，才标记 `AUTOMATED_GUARD`。
5. 行为或防护变化后同步更新 maturity、Evidence 和映射表。

```markdown
# LESSON-XXX-001 标题

Category: Network / WSL / OpenClaw / Browser / Security / Validation / Git / Tooling
Maturity: OBSERVED / CONFIRMED / LESSON / ENGINEERING_RULE / AUTOMATED_GUARD
Context:
Symptom:
Root Cause:
Diagnosis:
Temporary Fix:
Permanent Fix:
Lesson Learned:
Engineering Rule:
Automated Guard:
Evidence:
- code / tests / commits / docs / runtime evidence
Related:
Next Improvement:
```

## 当前索引

| Category | Lessons |
| --- | --- |
| Network | [Network 与 Proxy](lessons-learned/network/network-proxy.md) |
| WSL | [运行环境边界](lessons-learned/wsl/runtime-boundaries.md) |
| OpenClaw | [Gateway 与 Browser readiness](lessons-learned/openclaw/runtime-readiness.md) |
| Browser | [Browser validation](lessons-learned/browser/browser-validation.md) |
| Security | [Scanner 与 evidence](lessons-learned/security/scanner-evidence.md) |
| Validation | [Validation integrity](lessons-learned/validation/validation-integrity.md)、[Quality Gate](lessons-learned/validation/quality-gate.md) |
| Git | [认证与 identity](lessons-learned/git/git-authentication.md) |
| Tooling | [Python 与安全工具安装](lessons-learned/tooling/python-security-tools.md) |

Engineering Rules：[Network](engineering-rules/network-rules.md) · [Security](engineering-rules/security-rules.md) · [Validation](engineering-rules/validation-rules.md) · [Execution](engineering-rules/execution-rules.md) · [Git](engineering-rules/git-rules.md)

## Automated Guard 映射

| Lesson | Engineering Rule | Existing Guard | 状态 |
| --- | --- | --- | --- |
| NET-001 | Loopback always DIRECT | `ProxyEnvironmentService`, `NetworkAwareHttpClientFactory`, network tests | 已自动化 |
| NET-002 | DIRECT clears inherited proxies | `ProxyEnvironmentServiceTest` | 已自动化 |
| NET-003 | AUTO discovery fails explicitly | `WindowsHostResolver`, `WindowsHostResolverTest` | 已自动化 |
| OCL-002 | Transport success is not assertion success | Browser result envelope regression tests | 已自动化 |
| BRW-001 | Infrastructure error differs from assertion failure | `BrowserValidationProvider` tests | 已自动化 |
| BRW-002 | Browser evidence uses Artifact | Browser provider and real E2E | 已自动化 |
| SEC-001—006 | Scanner semantics, redaction, fingerprint | security service/parser/redactor tests and real E2E | 已自动化 |
| VAL-001—003 | Status, evidence, objective READ_ONLY checks | Validation services and E2E tests | 已自动化 |
| QGT-001—003 | Backend enforcement and current evidence | gate services and enforcement tests | 已自动化 |
| NET-004 | Diagnose apt source independently | 无 | Lesson only |
| WSL-001 | Separate sandbox/host/production runtime | 无统一诊断 guard | Confirmed only |
| OCL-001 | Probe Gateway, profile, CDP and navigation separately | 部分 availability probe；无完整 readiness aggregate | Lesson only |
| GIT-001—002 | Persistent, project-scoped SSH identity | 无 | Lesson only |
| TOL-001—002 | Isolated Python/CLI installation | 无 | Lesson only |

当前共 25 条：`OBSERVED` 1、`CONFIRMED` 1、`LESSON` 5、`ENGINEERING_RULE` 0、`AUTOMATED_GUARD` 18。
