package com.aidevos.orchestrator.modelregistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-configurable model binding: a concrete model id backed by a
 * registered provider and executed through a specific executor type.
 */
public class ModelDefinition {

	private String modelId;
	private String displayName;
	private String providerId;
	private String executorType;
	private boolean enabled = true;
	private List<String> capabilities = new ArrayList<>();

	public ModelDefinition() {
	}

	public String getModelId() {
		return modelId;
	}

	public void setModelId(String modelId) {
		this.modelId = modelId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getProviderId() {
		return providerId;
	}

	public void setProviderId(String providerId) {
		this.providerId = providerId;
	}

	public String getExecutorType() {
		return executorType;
	}

	public void setExecutorType(String executorType) {
		this.executorType = executorType;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(List<String> capabilities) {
		this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
	}
}
