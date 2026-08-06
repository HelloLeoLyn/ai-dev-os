package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent detail view including configuration, last activity and recent
 * executions.
 */
public record AgentDetailDTO(
		String agentId,
		String name,
		String type,
		AgentRuntimeStatus status,
		List<String> capabilities,
		Map<String, Object> configuration,
		Instant lastActivity,
		List<AgentExecutionSummary> executions) {

	public AgentDetailDTO {
		capabilities = capabilities == null ? List.of()
			: Collections.unmodifiableList(new ArrayList<>(capabilities));
		configuration = configuration == null ? Map.of()
			: Collections.unmodifiableMap(new LinkedHashMap<>(configuration));
		executions = executions == null ? List.of()
			: Collections.unmodifiableList(new ArrayList<>(executions));
	}
}
