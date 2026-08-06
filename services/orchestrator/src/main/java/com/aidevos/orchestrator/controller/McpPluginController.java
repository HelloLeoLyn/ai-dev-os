package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.mcpplugin.McpPlugin;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP plugin management API: list plugins, view plugin detail and toggle the
 * enabled state.
 */
@RestController
@RequestMapping("/api/mcp/plugins")
public class McpPluginController {

	private final McpPluginRegistryService registry;

	public McpPluginController(McpPluginRegistryService registry) {
		this.registry = registry;
	}

	@GetMapping
	public List<McpPlugin> list() {
		return registry.listPlugins();
	}

	@GetMapping("/{id}")
	public ResponseEntity<McpPlugin> get(@PathVariable String id) {
		return registry.getPlugin(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/enable")
	public ResponseEntity<McpPlugin> enable(@PathVariable String id) {
		return registry.enable(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/disable")
	public ResponseEntity<McpPlugin> disable(@PathVariable String id) {
		return registry.disable(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
