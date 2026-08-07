package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.agentcapability.AgentCapability;
import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.dashboard.AgentDetailDTO;
import com.aidevos.orchestrator.dashboard.AgentHistoryDTO;
import com.aidevos.orchestrator.dashboard.AgentRegistryService;
import com.aidevos.orchestrator.dashboard.AgentStatusDTO;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AgentController {

	private final AgentManager agentManager;
	private final AgentRegistryService agentRegistryService;
	private final AgentCapabilityResolver capabilityResolver;

	public AgentController(AgentManager agentManager, AgentRegistryService agentRegistryService,
			AgentCapabilityResolver capabilityResolver) {
		this.agentManager = agentManager;
		this.agentRegistryService = agentRegistryService;
		this.capabilityResolver = capabilityResolver;
	}

	@GetMapping("/api/agents")
	public List<AgentDefinition> getAllAgents() {
		return agentManager.getAllAgents();
	}

	@GetMapping("/api/agents/capabilities")
	public List<AgentCapability> listCapabilities() {
		return capabilityResolver.listCapabilities();
	}

	@GetMapping("/api/agents/capabilities/{capability}")
	public List<AgentDefinition> getAgentsByCapability(@PathVariable String capability) {
		return capabilityResolver.resolveByCapability(capability);
	}

	@GetMapping("/api/dashboard/agents")
	public List<AgentStatusDTO> getAgentRegistry() {
		return agentRegistryService.listAgents();
	}

	@GetMapping("/api/agents/{id}")
	public ResponseEntity<AgentDetailDTO> getAgentDetail(@PathVariable String id) {
		return ResponseEntity.ok(agentRegistryService.getAgentDetail(id)
			.orElseThrow(() -> new ResourceNotFoundException("Agent", id)));
	}

	@GetMapping("/api/agents/{id}/history")
	public ResponseEntity<AgentHistoryDTO> getAgentHistory(@PathVariable String id) {
		return ResponseEntity.ok(agentRegistryService.getAgentHistory(id)
			.orElseThrow(() -> new ResourceNotFoundException("Agent", id)));
	}
}
