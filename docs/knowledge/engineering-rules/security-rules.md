# Security Engineering Rules

1. 完整敏感值不得进入 Finding、Report、ValidationCheck、API、Audit、Timeline、UI、Artifact 或普通日志。
2. raw scanner output、normalized report 和 execution log 保存前必须 sanitized。
3. scanner execution success 与 findings presence 必须分开表达；findings 不代表 scanner failure。
4. unavailable、timeout、DB failure 和 parser error 均不得解释为 clean scan。
5. Finding 必须同时具有实例 `findingId` 与确定性的 `fingerprint`。
6. Provider 负责扫描与规范化；severity 是否 BLOCK/REVIEW 只由 Quality Gate 决定。
7. scanner 必须只扫描 Task 所属 workspace，且不得自动安装工具或扩大扫描范围。

实现依据：`SecurityValidationService`、`SecurityRedactor`、`SecurityFindingParser`、真实 scanner E2E（commits `6b6f783`, `deeb1fb`）。
