package com.aidevos.orchestrator.project.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Per-project agent registry: binds, unbinds and queries the agent types
 * enabled for each project, keeping project agent sets isolated from each
 * other.
 */
@Component
public class ProjectAgentRegistry {

	private final Map<String, Map<AgentType, ProjectAgentBinding>> bindings =
		new LinkedHashMap<>();
	private final AuditService auditService;

	public ProjectAgentRegistry() {
		this(AuditService.noop());
	}

	@Autowired
	public ProjectAgentRegistry(AuditService auditService) {
		this.auditService = auditService;
	}

	public ProjectAgentBinding bindAgent(String projectId, AgentType agentType, int priority) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("Project id is required");
		}
		if (agentType == null) {
			throw new IllegalArgumentException("Agent type is required");
		}
		ProjectAgentBinding binding = new ProjectAgentBinding(projectId.trim(), agentType,
			true, priority);
		bindings.computeIfAbsent(projectId.trim(), ignored -> new LinkedHashMap<>())
			.put(agentType, binding);
		auditService.projectEvent(EventType.PROJECT_AGENT_BOUND, projectId.trim(),
			"Agent bound to project: " + agentType.name(),
			Map.of("projectId", projectId.trim(), "agentType", agentType.name(),
				"priority", priority));
		return binding;
	}

	public boolean unbindAgent(String projectId, AgentType agentType) {
		Map<AgentType, ProjectAgentBinding> project = bindings.get(projectId);
		return project != null && project.remove(agentType) != null;
	}

	public List<ProjectAgentBinding> getProjectAgents(String projectId) {
		Map<AgentType, ProjectAgentBinding> project = bindings.get(projectId);
		if (project == null) {
			return List.of();
		}
		List<ProjectAgentBinding> result = new ArrayList<>(project.values());
		result.sort(Comparator.comparingInt(ProjectAgentBinding::priority));
		return List.copyOf(result);
	}

	public boolean isEnabled(String projectId, AgentType agentType) {
		ProjectAgentBinding binding = bindings.getOrDefault(projectId, Map.of()).get(agentType);
		return binding != null && binding.enabled();
	}
}
