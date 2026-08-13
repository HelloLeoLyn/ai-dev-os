package com.aidevos.orchestrator.validation.provider;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.validation.ValidationStatus;

public record ValidationCheckResult(ValidationStatus status, String summary,
		String errorMessage, String stdout, String stderr, List<String> reportPaths,
		Map<String, Object> metadata) {
	public static ValidationCheckResult skipped(String summary) {
		return new ValidationCheckResult(ValidationStatus.SKIPPED, summary, null, null, null,
			List.of(), Map.of());
	}
}
