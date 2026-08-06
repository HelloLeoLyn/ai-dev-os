package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {

	private AgentManager agentManager;
	private ExecutionRecordManager executionRecordManager;
	private McpProperties mcpProperties;
	private ObjectProvider<OpenClawWebSocketClient> openClawProvider;
	private AgentRegistryService registryService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		agentManager = new AgentManager();
		executionRecordManager = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository());
		mcpProperties = new McpProperties();
		openClawProvider = mock(ObjectProvider.class);
		registryService = new AgentRegistryService(agentManager, executionRecordManager,
			openClawProvider, mcpProperties);
	}

	@Test
	void shouldReportDisabledAgent() {
		agentManager.register(agent("coder", "codex", false));

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.DISABLED, status.status());
		assertFalse(status.enabled());
	}

	@Test
	void shouldReportIdleCodexAgent() {
		agentManager.register(agent("coder", "codex", true));

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.IDLE, status.status());
		assertTrue(status.enabled());
	}

	@Test
	void shouldReportRunningAgentWithActiveExecution() {
		agentManager.register(agent("coder", "codex", true));
		ExecutionRecord record = new ExecutionRecord();
		record.setId("record-1");
		record.setAgentName("coder");
		record.setStatus("RUNNING");
		record.setStartedAt(Instant.parse("2026-08-01T00:00:00Z"));
		executionRecordManager.save(record);

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.RUNNING, status.status());
		assertEquals(Instant.parse("2026-08-01T00:00:00Z"), status.lastHeartbeat());
	}

	@Test
	void shouldReportOnlineOpenClawAgentWhenConnected() {
		agentManager.register(agent("tester", "openclaw", true));
		OpenClawWebSocketClient client = mock(OpenClawWebSocketClient.class);
		when(client.isConnected()).thenReturn(true);
		when(openClawProvider.getIfAvailable()).thenReturn(client);

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.ONLINE, status.status());
	}

	@Test
	void shouldReportErrorOpenClawAgentWhenDisconnected() {
		agentManager.register(agent("tester", "openclaw", true));
		when(openClawProvider.getIfAvailable()).thenReturn(null);

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.ERROR, status.status());
	}

	@Test
	void shouldReportMcpAgentOnlineWhenMcpEnabled() {
		agentManager.register(agent("mcp-reader", "tool", true));
		mcpProperties.setEnabled(true);

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.ONLINE, status.status());
	}

	@Test
	void shouldReportMcpAgentErrorWhenMcpDisabled() {
		agentManager.register(agent("mcp-reader", "tool", true));

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals(AgentRuntimeStatus.ERROR, status.status());
	}

	@Test
	void shouldUseExternalIdAsAgentIdForOpenClaw() {
		AgentDefinition agent = agent("tester", "openclaw", true);
		agent.setExecutorConfig(java.util.Map.of("agentId", "main"));
		agentManager.register(agent);
		when(openClawProvider.getIfAvailable()).thenReturn(null);

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertEquals("main", status.agentId());
	}

	@Test
	void shouldReportNullHeartbeatWithoutActivity() {
		agentManager.register(agent("planner", "mock", true));

		AgentStatusDTO status = registryService.listAgents().getFirst();

		assertNull(status.lastHeartbeat());
	}

	private AgentDefinition agent(String name, String executor, boolean enabled) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name);
		agent.setExecutor(executor);
		agent.setType("system");
		agent.setEnabled(enabled);
		agent.setCapabilities(List.of("coding"));
		return agent;
	}
}
