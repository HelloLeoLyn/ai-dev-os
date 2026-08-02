package com.aidevos.orchestrator.plan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.tool.ToolAccess;

public record PlanSnapshot(List<AgentSnapshot> agents, Set<String> capabilities,
		List<ToolSnapshot> tools, Set<String> executors, String policyVersion,
		Map<String, Object> plannerMetadata) {

	public PlanSnapshot {
		agents = agents == null ? List.of() : List.copyOf(new ArrayList<>(agents));
		capabilities = capabilities == null ? Set.of()
			: Set.copyOf(new LinkedHashSet<>(capabilities));
		tools = tools == null ? List.of() : List.copyOf(new ArrayList<>(tools));
		executors = executors == null ? Set.of()
			: Set.copyOf(new LinkedHashSet<>(executors));
		plannerMetadata = PlanValues.freezeMap(plannerMetadata);
	}

	public record AgentSnapshot(String name, String executor, List<String> capabilities,
			String permissionLevel, boolean enabled) {
		public AgentSnapshot {
			capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
		}
	}

	public record ToolSnapshot(String providerId, String name, ToolAccess access) {
	}
}
