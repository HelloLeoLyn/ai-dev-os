package com.aidevos.orchestrator.plan;

import java.util.ArrayList;
import java.util.List;

public record AgentAssignment(String agentName, List<String> requiredCapabilities,
		List<String> fallbackAgentNames) {

	public AgentAssignment {
		requiredCapabilities = requiredCapabilities == null ? List.of()
			: List.copyOf(new ArrayList<>(requiredCapabilities));
		fallbackAgentNames = fallbackAgentNames == null ? List.of()
			: List.copyOf(new ArrayList<>(fallbackAgentNames));
	}
}
