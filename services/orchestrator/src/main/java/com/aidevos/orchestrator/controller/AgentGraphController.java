package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentDefinition;
import com.aidevos.orchestrator.agent.AgentRegistry;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent orchestration API: the agent registry (capabilities + status) and the
 * execution graph of a task. The legacy /api/agents controller is untouched.
 */
@RestController
public class AgentGraphController {

	private final AgentRegistry agentRegistry;
	private final AgentCoordinatorService agentCoordinatorService;

	public AgentGraphController(AgentRegistry agentRegistry,
			AgentCoordinatorService agentCoordinatorService) {
		this.agentRegistry = agentRegistry;
		this.agentCoordinatorService = agentCoordinatorService;
	}

	@GetMapping("/api/agents/registry")
	public List<AgentDefinition> listAgents() {
		return agentRegistry.listAgents();
	}

	@GetMapping("/api/tasks/{taskId}/graph")
	public ResponseEntity<ExecutionGraph> getGraph(@PathVariable String taskId) {
		return ResponseEntity.ok(agentCoordinatorService.getGraph(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("Task", taskId)));
	}
}
