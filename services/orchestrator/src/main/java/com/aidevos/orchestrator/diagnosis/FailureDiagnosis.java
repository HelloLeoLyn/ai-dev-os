package com.aidevos.orchestrator.diagnosis;

import java.time.Instant;
import java.util.List;

/**
 * V1 统一 Failure Diagnosis 模型。
 *
 * 按需同步生成（本地数据 <1s），不复制 Task/Execution/Delivery 状态机。
 * 正常人工 Gate（WAITING_APPROVAL 等）不产生 diagnosis。
 */
public record FailureDiagnosis(
	String taskId,
	/** 来源域：EXECUTION / DELIVERY / VALIDATION / SYSTEM */
	String source,
	/** 具体失败阶段（如 Execution/Maven、CI、QUALITY_GATE） */
	String stage,
	/** 失败 StepRun id（可空） */
	String failedStepId,
	/** 原始错误码（BUILD_FAILED / MODEL_NOT_FOUND / …） */
	String errorCode,
	/** 归一化诊断码（WRONG_WORKING_DIRECTORY / AGENT_DEFAULT_MODEL_MISSING / …） */
	String code,
	FailureCategory category,
	String summary,
	String rootCause,
	List<String> evidence,
	RecommendedAction recommendedAction,
	boolean retryable,
	String fingerprint,
	Instant diagnosedAt
) {
}
