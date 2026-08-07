package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.agentmarket.AgentPackage;
import com.aidevos.orchestrator.agentmarket.AgentRegistryService;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent market API: browse the agent catalog and install/uninstall agent
 * packages. Installing a package registers a usable AgentDefinition.
 */
@RestController
@RequestMapping("/api/agent-market")
public class AgentMarketController {

	private final AgentRegistryService registry;

	public AgentMarketController(AgentRegistryService registry) {
		this.registry = registry;
	}

	@GetMapping
	public List<AgentPackage> list() {
		return registry.listPackages();
	}

	@GetMapping("/{id}")
	public ResponseEntity<AgentPackage> get(@PathVariable String id) {
		return ResponseEntity.ok(registry.getPackage(id)
			.orElseThrow(() -> new ResourceNotFoundException("Agent package", id)));
	}

	@PostMapping("/{id}/install")
	public ResponseEntity<AgentPackage> install(@PathVariable String id) {
		registry.getPackage(id)
			.orElseThrow(() -> new ResourceNotFoundException("Agent package", id));
		return ResponseEntity.ok(registry.install(id));
	}

	@PostMapping("/{id}/uninstall")
	public ResponseEntity<AgentPackage> uninstall(@PathVariable String id) {
		registry.getPackage(id)
			.orElseThrow(() -> new ResourceNotFoundException("Agent package", id));
		return ResponseEntity.ok(registry.uninstall(id));
	}
}
