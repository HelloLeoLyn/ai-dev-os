package com.aidevos.orchestrator.plan;

public record ExpectedArtifact(String type, String name, String mediaType,
		boolean required, int minimumCount) {
}
