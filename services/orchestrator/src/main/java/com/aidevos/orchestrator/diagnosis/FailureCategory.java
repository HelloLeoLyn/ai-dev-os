package com.aidevos.orchestrator.diagnosis;

/**
 * V1 Failure Diagnosis category。确定性分类，不依赖 LLM。
 */
public enum FailureCategory {
	CODE,
	CONFIGURATION,
	ENVIRONMENT,
	PERMISSION,
	MODEL,
	TOOL,
	VALIDATION,
	DELIVERY,
	INFRASTRUCTURE,
	UNKNOWN
}
