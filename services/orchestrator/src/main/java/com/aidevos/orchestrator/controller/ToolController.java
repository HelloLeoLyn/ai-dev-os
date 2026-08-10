package com.aidevos.orchestrator.controller;

import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.mcp.tool.ToolDefinition;
import com.aidevos.orchestrator.mcp.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool layer API: lists the registered MCP tools and the tool invocation
 * audit trail for a task.
 */
@RestController
@RequestMapping("/api")
public class ToolController {

	private static final Set<EventType> TOOL_EVENTS = Set.of(
		EventType.TOOL_REGISTERED, EventType.TOOL_SELECTED, EventType.TOOL_STARTED,
		EventType.TOOL_COMPLETED, EventType.TOOL_FAILED, EventType.TOOL_DENIED);

	private final ToolRegistry registry;
	private final AuditService auditService;

	public ToolController(ToolRegistry registry) {
		this(registry, null);
	}

	@Autowired
	public ToolController(ToolRegistry registry, AuditService auditService) {
		this.registry = registry;
		this.auditService = auditService;
	}

	@GetMapping("/tools")
	public List<ToolDefinition> tools() {
		return registry.listTools();
	}

	@GetMapping("/tasks/{taskId}/tools")
	public List<EventRecord> taskTools(@PathVariable String taskId) {
		if (auditService == null) {
			return List.of();
		}
		return auditService.query(new EventQuery(null, null, null, null, null, null, null,
			null, null, null, TOOL_EVENTS, null, null, 0, 100)).stream()
			.filter(event -> taskId.equals(event.taskId()))
			.toList();
	}
}
