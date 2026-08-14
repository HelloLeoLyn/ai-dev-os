# Validation Engineering Rules

1. Validation outcome 必须区分成功、业务失败、基础设施错误、不适用和 capability 不可用。
2. 每个真实 check 必须留下 command/provider、exit code、duration、summary 与 Artifact references；大日志不得内嵌 ValidationRun。
3. 真实 E2E 不得 mock provider、伪造 finding/screenshot 或手工设置 PASS。
4. READ_ONLY 必须通过前后 Git HEAD、porcelain status 与 binary diff 客观证明。
5. Transport success 不得直接映射 business SUCCESS；Browser assertion 决定 browser check 业务结果。
6. Browser infrastructure error 与 assertion failure 必须使用不同状态和原因。
7. required provider unavailable 是未知 evidence，不得当作 PASS。

实现依据：Validation Center、Security、Browser provider 与 E2E（commits `9c9efd7`, `6b6f783`, `d39c99d`, `eead21c`）。
