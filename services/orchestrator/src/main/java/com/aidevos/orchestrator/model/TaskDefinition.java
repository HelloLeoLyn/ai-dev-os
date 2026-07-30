package com.aidevos.orchestrator.model;

import java.util.List;

public class TaskDefinition {

	private String id;
	private String name;
	private String description;
	private String agentName;
	private List<String> requiredCapabilities;
	private String status;

	public TaskDefinition() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public List<String> getRequiredCapabilities() {
		return requiredCapabilities;
	}

	public void setRequiredCapabilities(List<String> requiredCapabilities) {
		this.requiredCapabilities = requiredCapabilities;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
