package com.aidevos.orchestrator.validation;

public interface ValidationArtifactRepository {
	void save(ValidationArtifact artifact);
	ValidationArtifact get(String artifactId);
}
