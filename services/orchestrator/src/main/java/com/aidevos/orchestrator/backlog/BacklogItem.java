package com.aidevos.orchestrator.backlog;

import java.time.Instant;
import java.util.List;

public class BacklogItem {
	private String backlogItemId;
	private String title;
	private String description;
	private BacklogStatus status;
	private BacklogPriority priority;
	private String projectId;
	private String workspaceId;
	private BacklogSourceType sourceType;
	private String sourceReference;
	private String blockedReason;
	private List<String> dependsOn = List.of();
	private List<String> tags = List.of();
	private Instant createdAt;
	private Instant updatedAt;
	private String convertedTaskId;
	private Instant completedAt;

	public BacklogItem() { }

	public BacklogItem(String id, String title, String description, BacklogStatus status,
			BacklogPriority priority, String projectId, String workspaceId,
			BacklogSourceType sourceType, String sourceReference, List<String> dependsOn,
			List<String> tags, Instant now) {
		this.backlogItemId = id;
		this.title = title;
		this.description = description;
		this.status = status;
		this.priority = priority;
		this.projectId = projectId;
		this.workspaceId = workspaceId;
		this.sourceType = sourceType;
		this.sourceReference = sourceReference;
		this.dependsOn = copy(dependsOn);
		this.tags = copy(tags);
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String title, String description, BacklogPriority priority,
			String projectId, String workspaceId, BacklogSourceType sourceType,
			String sourceReference, List<String> dependsOn, List<String> tags, Instant now) {
		this.title = title;
		this.description = description;
		this.priority = priority;
		this.projectId = projectId;
		this.workspaceId = workspaceId;
		this.sourceType = sourceType;
		this.sourceReference = sourceReference;
		this.dependsOn = copy(dependsOn);
		this.tags = copy(tags);
		this.updatedAt = now;
	}

	public void changeStatus(BacklogStatus status, String blockedReason, Instant now) {
		this.status = status;
		this.blockedReason = status == BacklogStatus.BLOCKED ? blockedReason : null;
		this.updatedAt = now;
		if (status == BacklogStatus.DONE) this.completedAt = now;
	}

	public void converted(String taskId, Instant now) {
		this.convertedTaskId = taskId;
		changeStatus(BacklogStatus.CONVERTED, null, now);
	}

	public void bindContext(String projectId, String workspaceId, Instant now) {
		this.projectId = projectId;
		this.workspaceId = workspaceId;
		this.updatedAt = now;
	}

	private static List<String> copy(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}

	public String getBacklogItemId() { return backlogItemId; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public BacklogStatus getStatus() { return status; }
	public BacklogPriority getPriority() { return priority; }
	public String getProjectId() { return projectId; }
	public String getWorkspaceId() { return workspaceId; }
	public BacklogSourceType getSourceType() { return sourceType; }
	public String getSourceReference() { return sourceReference; }
	public String getBlockedReason() { return blockedReason; }
	public List<String> getDependsOn() { return dependsOn; }
	public List<String> getTags() { return tags; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public String getConvertedTaskId() { return convertedTaskId; }
	public Instant getCompletedAt() { return completedAt; }
	public void setBacklogItemId(String value) { backlogItemId = value; }
	public void setTitle(String value) { title = value; }
	public void setDescription(String value) { description = value; }
	public void setStatus(BacklogStatus value) { status = value; }
	public void setPriority(BacklogPriority value) { priority = value; }
	public void setProjectId(String value) { projectId = value; }
	public void setWorkspaceId(String value) { workspaceId = value; }
	public void setSourceType(BacklogSourceType value) { sourceType = value; }
	public void setSourceReference(String value) { sourceReference = value; }
	public void setBlockedReason(String value) { blockedReason = value; }
	public void setDependsOn(List<String> value) { dependsOn = copy(value); }
	public void setTags(List<String> value) { tags = copy(value); }
	public void setCreatedAt(Instant value) { createdAt = value; }
	public void setUpdatedAt(Instant value) { updatedAt = value; }
	public void setConvertedTaskId(String value) { convertedTaskId = value; }
	public void setCompletedAt(Instant value) { completedAt = value; }
}
