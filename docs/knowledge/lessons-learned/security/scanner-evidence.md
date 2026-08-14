# Security Validation Lessons

## LESSON-SEC-001 Scanner success + findings 不等于 scanner failure

Category: Security / Validation
Maturity: AUTOMATED_GUARD

Context: SAST、secret 与 dependency scanner 的职责是发现问题。
Symptom: 正常执行并发现 findings 的 scanner 被错误标记 FAILED。
Root Cause: 执行状态与风险判定混为一体。
Diagnosis: 独立检查 exit/parse outcome 与 findings count。
Temporary Fix: 在报告中分开记录 status 和 counts。
Permanent Fix: 扫描成功时 ValidationCheck 为 SUCCESS；报告使用 `FINDINGS_DETECTED`，风险交给 Quality Gate。
Lesson Learned: “发现问题”证明 scanner 工作正常。
Engineering Rule: Provider 只表达执行结果，Quality Gate 决定 BLOCK/REVIEW。
Automated Guard: `SecurityValidationService` 与真实 scanner E2E。
Evidence: `6b6f783`; `deeb1fb`; `SecurityScanStatus.java`.
Related: LESSON-SEC-006.
Next Improvement: 统一 scanner execution metrics。

## LESSON-SEC-002 Scanner unavailable 不等于 clean scan

Category: Security
Maturity: AUTOMATED_GUARD

Context: CLI 可能未安装或不可执行。
Symptom: 未运行扫描却显示 SUCCESS / zero findings。
Root Cause: availability 被当成空结果。
Diagnosis: 执行 version probe，并保留 AVAILABLE/UNAVAILABLE/ERROR。
Temporary Fix: 明确显示 NOT_AVAILABLE。
Permanent Fix: unavailable 映射 SKIPPED + NOT_AVAILABLE，required scanner 由 Gate 要求 REVIEW。
Lesson Learned: 没有证据不能解释为没有风险。
Engineering Rule: unavailable 永远不是 PASS。
Automated Guard: `SecurityScannerAvailability`、`SecurityScannerAvailabilityTest`、Gate tests。
Evidence: `6b6f783`; `8b2600c`.
Related: LESSON-VAL-001.
Next Improvement: availability diagnostics 增加安装指引链接。

## LESSON-SEC-003 Scanner error 不等于 zero findings

Category: Security
Maturity: AUTOMATED_GUARD

Context: Trivy DB、timeout 或 malformed JSON 会使扫描无有效结论。
Symptom: execution/parser error 被展示为零漏洞。
Root Cause: 缺少 `SCAN_ERROR` 与 clean result 的语义边界。
Diagnosis: 验证 exit code、timeout、parser outcome 和 report status。
Temporary Fix: 保留真实 error message。
Permanent Fix: error 映射 FAILED/SCAN_ERROR，不生成伪造的 clean report。
Lesson Learned: 失败产生的是未知风险，不是零风险。
Engineering Rule: 只有成功解析的 scanner output 才能声明 zero findings。
Automated Guard: malformed/unavailable tests 与 `SecurityValidationService` error branch。
Evidence: `6b6f783`; `SecurityFindingParserTest.java`.
Related: LESSON-SEC-002.
Next Improvement: 为 DB availability 建立独立 probe reason。

## LESSON-SEC-004 Scanner evidence 保存前必须脱敏

Category: Security
Maturity: AUTOMATED_GUARD

Context: Gitleaks raw output 可能包含匹配值、credential 字段或 execution context。
Symptom: 安全扫描自身成为敏感信息泄漏路径。
Root Cause: 原始 scanner JSON/log 被直接持久化。
Diagnosis: 对 raw、normalized、log、API 和 audit 执行 canary value 搜索。
Temporary Fix: 禁止暴露 raw artifact。
Permanent Fix: parser 受控处理后，所有 evidence 先经 `SecurityRedactor` 再保存。
Lesson Learned: 安全工具输出不天然安全。
Engineering Rule: scanner raw result、log 和 normalized report 必须 sanitized。
Automated Guard: redaction unit tests 与 `RealSecurityScannerEndToEndTest` zero-leak assertions。
Evidence: `6b6f783`; `deeb1fb`; `SecurityRedactor.java`.
Related: LESSON-SEC-005.
Next Improvement: 建立跨 Artifact 类型的通用 secret canary test。

## LESSON-SEC-005 Secret 不得进入任何传播面

Category: Security
Maturity: AUTOMATED_GUARD

Context: Finding 会经 repository、API、Audit、Timeline、UI 和 Artifact 传播。
Symptom: 只保证“不进 Git”，仍可能从其他系统面泄漏。
Root Cause: redaction scope 仅覆盖单一对象或单一输出。
Diagnosis: 使用 TEST ONLY canary 检查 Finding、Report、Check、API、Audit、Timeline、Artifact 与 log。
Temporary Fix: 停止发布受污染 evidence。
Permanent Fix: parser 不保留原值，持久化和输出前再次统一 redaction。
Lesson Learned: Secret safety 是端到端数据流属性。
Engineering Rule: 完整 secret 不得离开受控 parser/redactor boundary。
Automated Guard: security parser/redactor tests 和真实 E2E。
Evidence: `deeb1fb`; `SecurityFindingParserTest.java`; `RealSecurityScannerEndToEndTest.java`.
Related: [Security Rules](../../engineering-rules/security-rules.md).
Next Improvement: 对 frontend fixtures 执行同一 canary scan。

## LESSON-SEC-006 Finding 必须有 stable fingerprint

Category: Security
Maturity: AUTOMATED_GUARD

Context: findingId 可随机，但 rerun、baseline、suppression 与 gate evidence 需要稳定关联。
Symptom: 相同问题每次扫描都被当作新问题。
Root Cause: 使用随机 ID 作为唯一去重依据。
Diagnosis: 对相同 scanner/rule/file/line/package 输入重复解析并比较 fingerprint。
Temporary Fix: 用复合字段人工比对。
Permanent Fix: parser 基于稳定字段生成 fingerprint。
Lesson Learned: 实例标识与问题身份是两个概念。
Engineering Rule: findingId 可随机，fingerprint 必须确定性。
Automated Guard: `SecurityFindingParserTest.fingerprintIsStableAlthoughFindingIdIsNot`.
Evidence: `6b6f783`; `SecurityFinding.java`; `SecurityFindingParser.java`.
Related: LESSON-QGT-003.
Next Improvement: 定义跨 scanner dedup version。
