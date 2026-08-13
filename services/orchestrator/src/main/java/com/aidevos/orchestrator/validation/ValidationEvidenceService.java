package com.aidevos.orchestrator.validation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import org.springframework.stereotype.Service;

@Service
public class ValidationEvidenceService {
	private final ValidationArtifactRepository repository;
	private final ArtifactContentLimiter limiter;

	public ValidationEvidenceService(ValidationArtifactRepository repository,
			ArtifactContentLimiter limiter) {
		this.repository = repository;
		this.limiter = limiter;
	}

	public String saveLog(String runId, String checkId, String taskId, String content,
			Map<String, Object> metadata) {
		ExecutionArtifact limited = new ExecutionArtifact();
		limiter.apply(limited, content);
		ValidationArtifact artifact = new ValidationArtifact();
		artifact.setArtifactId("validation-artifact-" + UUID.randomUUID());
		artifact.setValidationRunId(runId);
		artifact.setCheckId(checkId);
		artifact.setTaskId(taskId);
		artifact.setName(checkId + ".log");
		artifact.setMediaType("text/plain");
		artifact.setContent(limited.getContent());
		artifact.setCreatedAt(Instant.now());
		Map<String, Object> details = new LinkedHashMap<>(limited.getMetadata());
		if (metadata != null) details.putAll(metadata);
		artifact.setMetadata(details);
		repository.save(artifact);
		return artifact.getArtifactId();
	}

	public String saveReference(String runId, String checkId, String taskId, String uri,
			Map<String, Object> metadata) {
		ValidationArtifact artifact = new ValidationArtifact();
		artifact.setArtifactId("validation-artifact-" + UUID.randomUUID());
		artifact.setValidationRunId(runId);
		artifact.setCheckId(checkId);
		artifact.setTaskId(taskId);
		artifact.setName("external-report");
		artifact.setMediaType("text/uri-list");
		artifact.setUri(uri);
		artifact.setCreatedAt(Instant.now());
		artifact.setMetadata(metadata);
		repository.save(artifact);
		return artifact.getArtifactId();
	}

	public ValidationArtifact get(String artifactId) { return repository.get(artifactId); }
}
