package com.aidevos.orchestrator.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentDefinition {

	private String name;
	private String executor;
	private Map<String, Object> executorConfig = new LinkedHashMap<>();
	private List<String> capabilities;
	private List<String> skillIds;
	private String type;
	private String description;
	private String version;
	private Instant updatedAt;
	private String permissionLevel;
	private boolean enabled = true;

	public AgentDefinition() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getExecutor() {
		return executor;
	}

	public void setExecutor(String executor) {
		this.executor = executor;
	}

	public Map<String, Object> getExecutorConfig() {
		return executorConfig;
	}

	public void setExecutorConfig(Map<String, Object> executorConfig) {
		this.executorConfig = executorConfig;
	}

	@Deprecated(forRemoval = true)
	public String getExternalId() {
		Object agentId = executorConfig == null ? null : executorConfig.get("agentId");
		return agentId instanceof String value ? value : null;
	}

	@Deprecated(forRemoval = true)
	public void setExternalId(String externalId) {
		if (executorConfig == null) {
			executorConfig = new LinkedHashMap<>();
		}
		executorConfig.put("agentId", externalId);
	}

	public List<String> getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(List<String> capabilities) {
		this.capabilities = capabilities;
	}

	public List<String> getSkillIds() {
		return skillIds;
	}

	public void setSkillIds(List<String> skillIds) {
		this.skillIds = skillIds;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getPermissionLevel() {
		return permissionLevel;
	}

	public void setPermissionLevel(String permissionLevel) {
		this.permissionLevel = permissionLevel;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
