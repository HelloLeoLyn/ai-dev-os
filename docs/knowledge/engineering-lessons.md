# Engineering Lessons

这些 Lesson 从 EPR 中提炼跨组件规则。`DOCUMENTED` 只表示知识已记录；只有真正落入 CI、Runtime 或 Planning guard 后，才可标为 `ENFORCED`。

| lessonId | title | category | status | derivedFromProblems |
|---|---|---|---|---|
| L-001 | LLM local IDs are not global identities | Identity | ENFORCED | EPR-008 |
| L-002 | Planner and Executor must share executable contracts | Executor / Artifact | ENFORCED | EPR-004, EPR-005 |
| L-003 | Approval grants a specific authority, not blanket task permission | Approval | RECOMMENDED | EPR-006 |
| L-004 | Retry cannot repair an already-persisted invalid source artifact | Recovery / Evidence | ENFORCED | EPR-007 |
| L-005 | Domain / DTO / persistence schema must agree on nullability and identity | Persistence | ENFORCED | EPR-003, EPR-008 |
| L-006 | Agent routing must be capability-driven | Executor / Routing | ENFORCED | EPR-004 |
| L-007 | Structured Evidence must have deterministic authority | Structured Output | ENFORCED | EPR-002, EPR-007 |
| L-008 | Browser host APIs require correct invocation context | Browser Runtime | ENFORCED | EPR-001 |

## L-001 — LLM local IDs are not global identities

- **lessonId:** L-001
- **title:** LLM local IDs are not global identities
- **category:** Identity
- **rule:** 模型输出的 ID 只能作为其 Analysis 内局部引用；持久化身份必须由服务端基于稳定 source 生成。
- **rationale:** 不同 Analysis 可以重复输出 R-001，直接持久化会造成状态、Decision 和 WorkItem 串线。
- **appliesTo:** Findings、Recommendations、事件、Backlog lineage、任何 LLM structured entity。
- **derivedFromProblems:** EPR-008
- **guardrails:** deterministic global ID；ambiguous local lookup 拒绝；source consistency check。
- **requiredTests:** same-local cross-analysis isolation、retry stability、ambiguous mutation rejection。
- **status:** ENFORCED

## L-002 — Planner and Executor must share executable contracts

- **lessonId:** L-002
- **title:** Planner and Executor must share executable contracts
- **category:** Executor / Artifact
- **rule:** Planner 的 capability、expected artifact 必须来自实际 Executor contract。
- **rationale:** 规划结果只有能被实际执行器满足才是可执行计划。
- **appliesTo:** Agent routing、PlanStep、Artifact Gate、Codex/Tool executor。
- **derivedFromProblems:** EPR-004, EPR-005
- **guardrails:** capability-based routing；single artifact contract source；expected-vs-actual tests。
- **requiredTests:** planner-first registry、coder contract、artifact matching。
- **status:** ENFORCED

## L-003 — Approval grants a specific authority, not blanket task permission

- **lessonId:** L-003
- **title:** Approval grants a specific authority, not blanket task permission
- **category:** Approval / Permission
- **rule:** 每个 Approval 必须说明 authority boundary、scope、active gate 和后续动作。
- **rationale:** Plan approval 不等于 workspace write、dangerous command 或 deployment approval。
- **appliesTo:** Plan、Job、Coding Approval、Tool permission、Deployment。
- **derivedFromProblems:** EPR-006
- **guardrails:** 独立 approval type；UI 明确 active gate；resume chain 可追踪。
- **requiredTests:** Plan CONSUMED + Coding PENDING；approve 后同 Job resume。
- **status:** RECOMMENDED

## L-004 — Retry cannot repair an already-persisted invalid source artifact

- **lessonId:** L-004
- **title:** Retry cannot repair an already-persisted invalid source artifact
- **category:** Recovery / Evidence
- **rule:** Retry Projection 只能重做派生处理；源 artifact 非法时必须报告 deterministic failure，不得伪造或放宽校验。
- **rationale:** 不重新运行 source Task，重读同一 payload 不会改变事实。
- **appliesTo:** Analysis Projection、artifact extraction、validation retry。
- **derivedFromProblems:** EPR-007
- **guardrails:** source Task 与 Projection 状态隔离；fingerprint/idempotency；strict validator。
- **requiredTests:** invalid history retry remains FAILED and creates no duplicate insight。
- **status:** ENFORCED

## L-005 — Domain / DTO / persistence schema must agree on nullability and identity

- **lessonId:** L-005
- **title:** Domain / DTO / persistence schema must agree on nullability and identity
- **category:** Persistence
- **rule:** 每个字段的 nullability、identity 和 ownership 必须在领域、接口、repository、migration 同时一致。
- **rationale:** 任一层收紧或改变语义都会在真实持久化路径产生隐蔽失败。
- **appliesTo:** PostgreSQL、JSON repository、DTO、migration、lineage。
- **derivedFromProblems:** EPR-003, EPR-008
- **guardrails:** migration contract review；round-trip persistence tests；fresh/upgrade validation。
- **requiredTests:** null round-trip、global identity migration、restart persistence。
- **status:** ENFORCED

## L-006 — Agent routing must be capability-driven

- **lessonId:** L-006
- **title:** Agent routing must be capability-driven
- **category:** Executor / Routing
- **rule:** Agent 选择必须由 intent、execution mode 和 capabilities 决定，不能依赖 registry 顺序。
- **rationale:** Registry 顺序是配置偶然性，不是执行语义。
- **appliesTo:** Hermes、planner、coder、analyst、executor registry。
- **derivedFromProblems:** EPR-004
- **guardrails:** required capability predicate；拒绝 mock 作为 READ_WRITE implementation executor。
- **requiredTests:** planner-first ordering、READ_ONLY non-regression。
- **status:** ENFORCED

## L-007 — Structured Evidence must have deterministic authority

- **lessonId:** L-007
- **title:** Structured Evidence must have deterministic authority
- **category:** Structured Output
- **rule:** EvidenceRef 只能指向可由系统确定归属的 artifact、workspace file 或受控 execution identity。
- **rationale:** 命令名称、自然语言和猜测路径不是 authority。
- **appliesTo:** Analysis、Findings、Recommendations、validation artifacts。
- **derivedFromProblems:** EPR-002, EPR-007
- **guardrails:** schema allow-list；boundary validator；artifact existence check。
- **requiredTests:** source boundary、missing artifact、workspace escape。
- **status:** ENFORCED

## L-008 — Browser host APIs require correct invocation context

- **lessonId:** L-008
- **title:** Browser host APIs require correct invocation context
- **category:** Browser Runtime
- **rule:** window/globalThis host methods 必须通过正确 receiver 或 wrapper 调用。
- **rationale:** 宿主对象方法可能依赖内部 receiver，脱离调用会触发 Illegal invocation。
- **appliesTo:** timer、storage、DOM/browser host APIs。
- **derivedFromProblems:** EPR-001
- **guardrails:** host API wrappers；browser-like receiver regression tests。
- **requiredTests:** timer schedule/cancel、错误 receiver test。
- **status:** ENFORCED

## Promotion guidance

- **Hermes Planning Guardrail:** L-001、L-002、L-006、L-007。
- **CI/Test Gate:** L-002、L-004、L-005、L-007、L-008。
- **当前仅建议、不声称已强制：** L-003，直到统一多审批模型和所有 active gate projection 完成。
