package com.aidevos.orchestrator.modelregistry;

/**
 * Result of trusted server-side model resolution. credentialRef is the
 * environment-variable / secret reference name only; the actual value is
 * resolved at execution time and injected into the subprocess environment.
 */
public record ResolvedModel(
		String requestedModelId,
		String resolvedModelId,
		String providerId,
		String executorType,
		String baseUrl,
		String credentialRef) {

	public ResolvedModel {
		requestedModelId = requestedModelId == null || requestedModelId.isBlank()
			? "AUTO" : requestedModelId;
	}
}
