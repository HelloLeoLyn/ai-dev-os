package com.aidevos.orchestrator.memory;

import java.time.Instant;

/**
 * One entry of long-term project memory, readable by agents later.
 */
public class MemoryRecord {

	private String id;
	private String projectId;
	private MemoryType type;
	private String key;
	private String content;
	private Instant createdAt;
	private Instant updatedAt;

	public MemoryRecord() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public MemoryType getType() {
		return type;
	}

	public void setType(MemoryType type) {
		this.type = type;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
