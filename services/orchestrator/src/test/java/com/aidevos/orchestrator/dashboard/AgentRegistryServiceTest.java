package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.openclaw.client.OpenClawWebSocketClient;
import com.aidevos.orchestrator.tool.mcp.McpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRegistryServiceTest {

	private AgentManager agentManager;
	private ExecutionRecordManager executionRecordManager;
	private McpProperties mcpProperties;
	private ObjectProvider<OpenClawWebSocketClient> openClawProvider;
	private AgentRegistryService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		agentManager = new AgentManager();
		executionRecordManager = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository());
		mcpProperties = new McpProperties();
		openClawProvider = mock(ObjectProvider.class);
		service = new AgentRegistryService(agentManager, executionRecordManager,
			openClawProvider, mcpProperties);
	}

	@Test
	void shouldReturnDetailByAgentIdOrName() {
		AgentDefinition agent = agent("tester", "openclaw", true);
		agent.setExecutorConfig(new LinkedHashMap<>(Map.of("agentId", "main")));
		agentManager.register(agent);
		when(openClawProvider.getIfAvailable()).thenReturn(null);

		Optional<AgentDetailDTO> byAgentId = service.getAgentDetail("main");
		Optional<AgentDetailDTO> byName = service.getAgentDetail("tester");

		assertTrue(byAgentId.isPresent());
		assertEquals("main", byAgentId.get().agentId());
		assertEquals("tester", byName.get().name());
		assertEquals("main", byName.get().configuration().get("agentId"));
		assertEquals(List.of("testing", "browser"), byName.get().capabilities());
		assertEquals(AgentRuntimeStatus.ERROR, byName.get().status());
	}

	@Test
	void shouldTolerateNullConfigurationValues() {
		AgentDefinition agent = agent("coder", "codex", true);
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("workspace", null);
		config.put("model", null);
		agent.setExecutorConfig(config);
		agentManager.register(agent);

		Optional<AgentDetailDTO> detail = service.getAgentDetail("coder");

		assertTrue(detail.isPresent());
		assertEquals(2, detail.get().configuration().size());
	}

	@Test
	void shouldReturnDetailWithRecentExecutions() {
		agentManager.register(agent("coder", "codex", true));
		executionRecordManager.save(record("record-1", "coder", "exec-1", "SUCCESS",
			Instant.parse("2026-08-01T00:00:00Z"), "ok"));
		executionRecordManager.save(record("record-2", "coder", "exec-2", "FAILED",
			Instant.parse("2026-08-01T01:00:00Z"), "boom"));

		Optional<AgentDetailDTO> detail = service.getAgentDetail("coder");

		assertTrue(detail.isPresent());
		assertEquals(2, detail.get().executions().size());
		assertEquals("exec-2", detail.get().executions().getFirst().executionId());
		assertEquals(Instant.parse("2026-08-01T01:00:00Z"), detail.get().lastActivity());
	}

	@Test
	void shouldReturnHistoryWithCountsAndLastError() {
		agentManager.register(agent("coder", "codex", true));
		executionRecordManager.save(record("record-1", "coder", "exec-1", "SUCCESS",
			Instant.parse("2026-08-01T00:00:00Z"), "ok"));
		executionRecordManager.save(record("record-2", "coder", "exec-2", "FAILED",
			Instant.parse("2026-08-01T01:00:00Z"), "boom"));
		executionRecordManager.save(record("record-3", "coder", "exec-3", "FAILED",
			Instant.parse("2026-08-01T02:00:00Z"), "again"));

		Optional<AgentHistoryDTO> history = service.getAgentHistory("coder");

		assertTrue(history.isPresent());
		assertEquals(3, history.get().recentExecutions().size());
		assertEquals(1, history.get().successCount());
		assertEquals(2, history.get().failedCount());
		assertEquals("again", history.get().lastError());
	}

	@Test
	void shouldReturnEmptyForUnknownAgent() {
		assertTrue(service.getAgentDetail("missing").isEmpty());
		assertTrue(service.getAgentHistory("missing").isEmpty());
	}

	private AgentDefinition agent(String name, String executor, boolean enabled) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name);
		agent.setExecutor(executor);
		agent.setType("system");
		agent.setEnabled(enabled);
		agent.setCapabilities(List.of("testing", "browser"));
		return agent;
	}

	private ExecutionRecord record(String id, String agentName, String executionId,
			String status, Instant startedAt, String message) {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setAgentName(agentName);
		record.setExecutionId(executionId);
		record.setJobId("job-1");
		record.setStatus(status);
		record.setStartedAt(startedAt);
		record.setMessage(message);
		return record;
	}
}
