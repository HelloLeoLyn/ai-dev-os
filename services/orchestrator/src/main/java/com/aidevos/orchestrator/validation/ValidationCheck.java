package com.aidevos.orchestrator.validation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValidationCheck {
	private String checkId;
	private ValidationCheckType type;
	private String name;
	private ValidationStatus status = ValidationStatus.PENDING;
	private boolean required;
	private boolean blocking;
	private Instant startedAt;
	private Instant completedAt;
	private long durationMs;
	private String summary;
	private String errorMessage;
	private List<String> artifactIds = new ArrayList<>();
	private Map<String, Object> metadata = new LinkedHashMap<>();

	public ValidationCheck() { }

	public ValidationCheck(String checkId, ValidationCheckType type, String name,
			boolean required, boolean blocking) {
		this.checkId = checkId;
		this.type = type;
		this.name = name;
		this.required = required;
		this.blocking = blocking;
	}

	public String getCheckId() { return checkId; }
	public void setCheckId(String checkId) { this.checkId = checkId; }
	public ValidationCheckType getType() { return type; }
	public void setType(ValidationCheckType type) { this.type = type; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public ValidationStatus getStatus() { return status; }
	public void setStatus(ValidationStatus status) { this.status = status; }
	public boolean isRequired() { return required; }
	public void setRequired(boolean required) { this.required = required; }
	public boolean isBlocking() { return blocking; }
	public void setBlocking(boolean blocking) { this.blocking = blocking; }
	public Instant getStartedAt() { return startedAt; }
	public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
	public long getDurationMs() { return durationMs; }
	public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
	public String getSummary() { return summary; }
	public void setSummary(String summary) { this.summary = summary; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
	public List<String> getArtifactIds() { return artifactIds; }
	public void setArtifactIds(List<String> artifactIds) {
		this.artifactIds = artifactIds == null ? new ArrayList<>() : new ArrayList<>(artifactIds);
	}
	public Map<String, Object> getMetadata() { return metadata; }
	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
	}
}
