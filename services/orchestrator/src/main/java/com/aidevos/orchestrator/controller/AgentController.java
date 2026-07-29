package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

	private final AgentManager agentManager;

	public AgentController(AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	@GetMapping
	public List<AgentDefinition> getAllAgents() {
		return agentManager.getAllAgents();
	}
}
