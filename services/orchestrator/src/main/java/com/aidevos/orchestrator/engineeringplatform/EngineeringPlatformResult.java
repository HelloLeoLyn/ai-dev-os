package com.aidevos.orchestrator.engineeringplatform;

import java.util.LinkedHashMap;
import java.util.Map;

public record EngineeringPlatformResult(EngineeringPlatformOperation operation, int exitCode,
		EngineeringPlatformStatus status, String stdout, String stderr, long durationMs,
		Map<String, Object> commandMetadata) {

	public EngineeringPlatformResult {
		commandMetadata = commandMetadata == null ? Map.of()
			: Map.copyOf(new LinkedHashMap<>(commandMetadata));
	}
}
