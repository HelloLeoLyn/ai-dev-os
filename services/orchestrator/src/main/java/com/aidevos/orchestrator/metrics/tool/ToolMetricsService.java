package com.aidevos.orchestrator.metrics.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.stereotype.Service;

/**
 * Tool observability: aggregates MCP tool usage (execution, success, failure,
 * denial counts and the average duration) from the existing mcp-tool audit
 * events. Read-only and computed on demand.
 */
@Service
public class ToolMetricsService {

	private static final Set<EventType> TOOL_EVENTS = Set.of(EventType.TOOL_STARTED,
		EventType.TOOL_COMPLETED, EventType.TOOL_FAILED, EventType.TOOL_DENIED);

	private final AuditService auditService;

	public ToolMetricsService(AuditService auditService) {
		this.auditService = auditService;
	}

	public List<ToolMetrics> listToolMetrics() {
		Map<String, ToolAggregate> aggregates = new LinkedHashMap<>();
		for (EventRecord event : auditService.query(new EventQuery("mcp-tool", null, null,
				null, null, null, null, null, null, null, TOOL_EVENTS, null, null, 0,
				EventQuery.MAX_LIMIT))) {
			String toolId = metadataString(event, "toolId");
			if (toolId == null || toolId.isBlank()) {
				toolId = event.aggregateId();
			}
			if (toolId == null || toolId.isBlank()) {
				continue;
			}
			ToolAggregate aggregate = aggregates.computeIfAbsent(toolId,
				ToolAggregate::new);
			aggregate.apply(event);
		}
		List<ToolMetrics> result = new ArrayList<>();
		for (ToolAggregate aggregate : aggregates.values()) {
			result.add(aggregate.toMetrics());
		}
		result.sort((left, right) -> Long.compare(right.executeCount(), left.executeCount()));
		return result;
	}

	public ToolMetrics getToolMetrics(String toolId) {
		if (toolId == null || toolId.isBlank()) {
			return new ToolMetrics(toolId, 0, 0, 0, 0, 0);
		}
		return listToolMetrics().stream()
			.filter(metrics -> toolId.equals(metrics.toolId()))
			.findFirst()
			.orElseGet(() -> new ToolMetrics(toolId, 0, 0, 0, 0, 0));
	}

	private static String metadataString(EventRecord event, String key) {
		Object value = event.metadata() == null ? null : event.metadata().get(key);
		return value == null ? null : String.valueOf(value);
	}

	private static long metadataDuration(EventRecord event) {
		Object value = event.metadata() == null ? null : event.metadata().get("duration");
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value != null) {
			try {
				return Long.parseLong(String.valueOf(value));
			}
			catch (NumberFormatException ignored) {
				return 0;
			}
		}
		return 0;
	}

	private static final class ToolAggregate {

		private final String toolId;
		private long executeCount;
		private long successCount;
		private long failedCount;
		private long deniedCount;
		private long totalDuration;
		private long measuredCount;

		private ToolAggregate(String toolId) {
			this.toolId = toolId;
		}

		private void apply(EventRecord event) {
			switch (event.type()) {
				case TOOL_STARTED -> executeCount++;
				case TOOL_COMPLETED -> {
					successCount++;
					measure(event);
				}
				case TOOL_FAILED -> {
					failedCount++;
					measure(event);
				}
				case TOOL_DENIED -> deniedCount++;
				default -> {
					// Ignore unrelated events.
				}
			}
		}

		private void measure(EventRecord event) {
			totalDuration += metadataDuration(event);
			measuredCount++;
		}

		private ToolMetrics toMetrics() {
			long average = measuredCount == 0 ? 0 : totalDuration / measuredCount;
			return new ToolMetrics(toolId, executeCount, successCount, failedCount,
				deniedCount, average);
		}
	}
}
