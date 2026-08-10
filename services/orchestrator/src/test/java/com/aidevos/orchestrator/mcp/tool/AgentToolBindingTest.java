package com.aidevos.orchestrator.mcp.tool;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Phase 16-C: AgentSelector tool capability binding. CODEX gets git +
 * filesystem, OPENCLAW gets browser, TEST_AGENT gets terminal.
 */
class AgentToolBindingTest {

	private AgentSelector selector;

	@BeforeEach
	void setUp() {
		ToolRegistry registry = new InMemoryToolRegistry(List.of(
			new GitToolExecutor(mock(GitCommandExecutor.class)),
			new FilesystemToolExecutor(),
			new BrowserToolExecutor(mock(com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor.class)),
			new DockerToolExecutor(),
			new TerminalToolExecutor()));
		McpToolRouter router = new McpToolRouter(registry,
			new AuditService(new InMemoryAuditRepository()));
		selector = new AgentSelector(new AgentManager(), null, router);
	}

	@Test
	void shouldBindGitAndFilesystemToCodex() {
		List<String> tools = selector.selectTools(AgentType.CODEX).stream()
			.map(ToolDefinition::toolId).toList();

		assertTrue(tools.contains("git"));
		assertTrue(tools.contains("filesystem"));
	}

	@Test
	void shouldBindGitAndFilesystemToRepairAgent() {
		List<String> tools = selector.selectTools(AgentType.REPAIR_AGENT).stream()
			.map(ToolDefinition::toolId).toList();

		assertTrue(tools.contains("git"));
		assertTrue(tools.contains("filesystem"));
	}

	@Test
	void shouldBindBrowserToOpenClaw() {
		List<String> tools = selector.selectTools(AgentType.OPENCLAW).stream()
			.map(ToolDefinition::toolId).toList();

		assertTrue(tools.contains("browser"));
	}

	@Test
	void shouldBindTerminalToTestAgent() {
		List<String> tools = selector.selectTools(AgentType.TEST_AGENT).stream()
			.map(ToolDefinition::toolId).toList();

		assertTrue(tools.contains("terminal"));
	}

	@Test
	void shouldReturnNoToolsForHermes() {
		assertTrue(selector.selectTools(AgentType.HERMES).isEmpty());
	}
}
