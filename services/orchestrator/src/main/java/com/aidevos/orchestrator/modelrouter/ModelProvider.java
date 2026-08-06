package com.aidevos.orchestrator.modelrouter;

/**
 * Provider configuration entry from models.yaml. type is LLM for API-backed
 * providers (OpenAI, DeepSeek) or AGENT for agent-backed providers (Codex,
 * OpenClaw).
 */
public class ModelProvider {

	private String providerId;
	private String name;
	private String type;
	private String model;
	private boolean enabled = true;

	public ModelProvider() {
	}

	public String getProviderId() {
		return providerId;
	}

	public void setProviderId(String providerId) {
		this.providerId = providerId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
