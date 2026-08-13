package com.aidevos.orchestrator.validation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ValidationArtifact {
	private String artifactId;
	private String validationRunId;
	private String checkId;
	private String taskId;
	private String name;
	private String mediaType;
	private String content;
	private String uri;
	private Instant createdAt;
	private Map<String, Object> metadata = new LinkedHashMap<>();

	public ValidationArtifact() { }
	public String getArtifactId() { return artifactId; }
	public void setArtifactId(String value) { artifactId = value; }
	public String getValidationRunId() { return validationRunId; }
	public void setValidationRunId(String value) { validationRunId = value; }
	public String getCheckId() { return checkId; }
	public void setCheckId(String value) { checkId = value; }
	public String getTaskId() { return taskId; }
	public void setTaskId(String value) { taskId = value; }
	public String getName() { return name; }
	public void setName(String value) { name = value; }
	public String getMediaType() { return mediaType; }
	public void setMediaType(String value) { mediaType = value; }
	public String getContent() { return content; }
	public void setContent(String value) { content = value; }
	public String getUri() { return uri; }
	public void setUri(String value) { uri = value; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant value) { createdAt = value; }
	public Map<String, Object> getMetadata() { return metadata; }
	public void setMetadata(Map<String, Object> value) {
		metadata = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
	}
}
