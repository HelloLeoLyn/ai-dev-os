package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.List;

/**
 * Read-only agent registry view for the dashboard.
 */
public record AgentStatusDTO(
		String agentId,
		String name,
		String type,
		AgentRuntimeStatus status,
		boolean enabled,
		List<String> capabilities,
		Instant lastHeartbeat) {

	public AgentStatusDTO {
		capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
	}
}
