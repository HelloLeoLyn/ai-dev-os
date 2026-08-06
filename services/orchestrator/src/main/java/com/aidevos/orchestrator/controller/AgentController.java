package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.dashboard.AgentDetailDTO;
import com.aidevos.orchestrator.dashboard.AgentHistoryDTO;
import com.aidevos.orchestrator.dashboard.AgentRegistryService;
import com.aidevos.orchestrator.dashboard.AgentStatusDTO;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgentController {

	private final AgentManager agentManager;
	private final AgentRegistryService agentRegistryService;

	public AgentController(AgentManager agentManager, AgentRegistryService agentRegistryService) {
		this.agentManager = agentManager;
		this.agentRegistryService = agentRegistryService;
	}

	@GetMapping("/api/agents")
	public List<AgentDefinition> getAllAgents() {
		return agentManager.getAllAgents();
	}

	@GetMapping("/api/dashboard/agents")
	public List<AgentStatusDTO> getAgentRegistry() {
		return agentRegistryService.listAgents();
	}

	@GetMapping("/api/agents/{id}")
	public ResponseEntity<AgentDetailDTO> getAgentDetail(@PathVariable String id) {
		return agentRegistryService.getAgentDetail(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/api/agents/{id}/history")
	public ResponseEntity<AgentHistoryDTO> getAgentHistory(@PathVariable String id) {
		return agentRegistryService.getAgentHistory(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
