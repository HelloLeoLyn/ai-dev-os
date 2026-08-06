package com.aidevos.orchestrator.testagent;

import java.util.List;

/**
 * Logical Testing Agent. Runs generated test commands through the command
 * execution abstraction layer (not bound to a specific executor such as
 * OpenClaw).
 */
public class TestAgent {

	private final String agentId;
	private final String name;
	private final String type;
	private final List<String> capabilities;

	public TestAgent(String agentId, String name, String type, List<String> capabilities) {
		this.agentId = agentId;
		this.name = name;
		this.type = type;
		this.capabilities = List.copyOf(capabilities);
	}

	public String getAgentId() {
		return agentId;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public List<String> getCapabilities() {
		return capabilities;
	}
}
