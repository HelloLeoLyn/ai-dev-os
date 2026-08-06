package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.openclaw.client.OpenClawWebSocketClient;
import com.aidevos.orchestrator.tool.mcp.McpProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Agent registry status and history derived from registered agents, OpenClaw
 * gateway connectivity, MCP configuration and recent execution activity.
 * Read-only; no execution flow is changed.
 */
@Service
public class AgentRegistryService {

	private static final int RECENT_EXECUTION_LIMIT = 10;

	private final AgentManager agentManager;
	private final ExecutionRecordManager executionRecordManager;
	private final ObjectProvider<OpenClawWebSocketClient> openClawClient;
	private final McpProperties mcpProperties;

	public AgentRegistryService(AgentManager agentManager,
			ExecutionRecordManager executionRecordManager,
			ObjectProvider<OpenClawWebSocketClient> openClawClient,
			McpProperties mcpProperties) {
		this.agentManager = agentManager;
		this.executionRecordManager = executionRecordManager;
		this.openClawClient = openClawClient;
		this.mcpProperties = mcpProperties;
	}

	public List<AgentStatusDTO> listAgents() {
		return agentManager.getAllAgents().stream()
			.map(this::statusOf)
			.toList();
	}

	public Optional<AgentDetailDTO> getAgentDetail(String id) {
		return findAgent(id).map(this::detail);
	}

	public Optional<AgentHistoryDTO> getAgentHistory(String id) {
		return findAgent(id).map(agent -> history(agent.getName()));
	}

	private Optional<AgentDefinition> findAgent(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return agentManager.getAllAgents().stream()
			.filter(agent -> id.equals(agentId(agent)) || id.equals(agent.getName()))
			.findFirst();
	}

	private AgentDetailDTO detail(AgentDefinition agent) {
		return new AgentDetailDTO(agentId(agent), agent.getName(), agent.getType(),
			runtimeStatus(agent), capabilities(agent), configuration(agent),
			lastActivity(agent.getName()), recentExecutions(agent.getName()));
	}

	private AgentHistoryDTO history(String agentName) {
		List<ExecutionRecord> records = executionRecordManager.getAll().stream()
			.filter(record -> agentName.equals(record.getAgentName()))
			.toList();
		long success = records.stream().filter(record -> statusIs(record, "SUCCESS")).count();
		long failed = records.stream().filter(record -> statusIs(record, "FAILED")).count();
		List<AgentExecutionSummary> recent = records.stream()
			.sorted(Comparator.comparing(this::activityTime,
				Comparator.nullsLast(Comparator.naturalOrder())).reversed())
			.limit(RECENT_EXECUTION_LIMIT)
			.map(this::executionSummary)
			.toList();
		return new AgentHistoryDTO(recent, Math.toIntExact(success), Math.toIntExact(failed),
			lastError(records));
	}

	private String lastError(List<ExecutionRecord> records) {
		return records.stream()
			.filter(record -> statusIs(record, "FAILED"))
			.map(ExecutionRecord::getMessage)
			.filter(Objects::nonNull)
			.reduce((first, second) -> second)
			.orElse(null);
	}

	private List<AgentExecutionSummary> recentExecutions(String agentName) {
		return executionRecordManager.getAll().stream()
			.filter(record -> agentName.equals(record.getAgentName()))
			.sorted(Comparator.comparing(this::activityTime,
				Comparator.nullsLast(Comparator.naturalOrder())).reversed())
			.limit(RECENT_EXECUTION_LIMIT)
			.map(this::executionSummary)
			.toList();
	}

	private AgentExecutionSummary executionSummary(ExecutionRecord record) {
		String executionId = record.getExecutionId() != null
			? record.getExecutionId() : record.getId();
		return new AgentExecutionSummary(executionId, record.getJobId(), record.getStatus(),
			record.getStartedAt(), record.getCompletedAt(), record.getMessage());
	}

	private List<String> capabilities(AgentDefinition agent) {
		return agent.getCapabilities() == null ? List.of() : agent.getCapabilities();
	}

	private Map<String, Object> configuration(AgentDefinition agent) {
		return agent.getExecutorConfig() == null
			? Map.of() : new LinkedHashMap<>(agent.getExecutorConfig());
	}

	private AgentStatusDTO statusOf(AgentDefinition agent) {
		return new AgentStatusDTO(agentId(agent), agent.getName(), agent.getType(),
			runtimeStatus(agent), agent.isEnabled(), capabilities(agent),
			lastActivity(agent.getName()));
	}

	private String agentId(AgentDefinition agent) {
		String externalId = agent.getExternalId();
		return externalId == null || externalId.isBlank() ? agent.getName() : externalId;
	}

	private AgentRuntimeStatus runtimeStatus(AgentDefinition agent) {
		if (!agent.isEnabled()) {
			return AgentRuntimeStatus.DISABLED;
		}
		if (isRunning(agent.getName())) {
			return AgentRuntimeStatus.RUNNING;
		}
		String executor = agent.getExecutor();
		if (executor == null) {
			return AgentRuntimeStatus.IDLE;
		}
		return switch (executor) {
			case "openclaw" -> {
				OpenClawWebSocketClient client = openClawClient.getIfAvailable();
				yield client != null && client.isConnected()
					? AgentRuntimeStatus.ONLINE : AgentRuntimeStatus.ERROR;
			}
			case "tool" -> mcpProperties.isEnabled()
				? AgentRuntimeStatus.ONLINE : AgentRuntimeStatus.ERROR;
			default -> AgentRuntimeStatus.IDLE;
		};
	}

	private boolean isRunning(String agentName) {
		return executionRecordManager.getAll().stream()
			.anyMatch(record -> agentName.equals(record.getAgentName())
				&& !isTerminal(record.getStatus()));
	}

	private boolean statusIs(ExecutionRecord record, String expected) {
		return record.getStatus() != null && record.getStatus().equalsIgnoreCase(expected);
	}

	private boolean isTerminal(String status) {
		return status != null && (status.equalsIgnoreCase("SUCCESS")
			|| status.equalsIgnoreCase("FAILED")
			|| status.equalsIgnoreCase("CANCELLED"));
	}

	private Instant lastActivity(String agentName) {
		return executionRecordManager.getAll().stream()
			.filter(record -> agentName.equals(record.getAgentName()))
			.map(this::activityTime)
			.filter(Objects::nonNull)
			.max(Instant::compareTo)
			.orElse(null);
	}

	private Instant activityTime(ExecutionRecord record) {
		return record.getStartedAt() != null ? record.getStartedAt() : record.getCompletedAt();
	}
}
