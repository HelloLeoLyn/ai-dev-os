package com.aidevos.orchestrator.validation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryValidationArtifactRepository implements ValidationArtifactRepository {
	private final Map<String, ValidationArtifact> artifacts = new ConcurrentHashMap<>();
	@Override public void save(ValidationArtifact artifact) { artifacts.put(artifact.getArtifactId(), artifact); }
	@Override public ValidationArtifact get(String artifactId) { return artifacts.get(artifactId); }
}
