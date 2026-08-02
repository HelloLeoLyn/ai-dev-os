package com.aidevos.orchestrator.plan;

public record ArtifactReference(String fromStepId, String artifactType, String artifactName,
		String inputKey, boolean required) {
}
