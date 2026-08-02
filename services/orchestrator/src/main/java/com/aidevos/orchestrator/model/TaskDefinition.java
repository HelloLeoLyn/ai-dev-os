package com.aidevos.orchestrator.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TaskDefinition {

	private String id;
	private String name;
	private String description;
	private String agentName;
	private List<String> requiredCapabilities;
	private Map<String, Object> parameters = new LinkedHashMap<>();
	private Map<String, Object> metadata = new LinkedHashMap<>();
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

	public Map<String, Object> getParameters() {
		return parameters;
	}

	public void setParameters(Map<String, Object> parameters) {
		this.parameters = parameters;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
