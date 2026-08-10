package com.aidevos.orchestrator.mcp.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Phase 16-C: ToolRegistry registration, lookup, listing and category
 * queries over the in-memory registry.
 */
class ToolRegistryTest {

	private final ToolRegistry registry = new InMemoryToolRegistry(List.of(
		new GitToolExecutor(mock(GitCommandExecutor.class)),
		new FilesystemToolExecutor(),
		new BrowserToolExecutor(mock(com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor.class)),
		new DockerToolExecutor(),
		new TerminalToolExecutor()));

	@Test
	void shouldRegisterAndLookupTools() {
		ToolDefinition git = registry.getTool("git");

		assertNotNull(git);
		assertEquals("git", git.toolId());
		assertEquals(ToolType.GIT, git.type());
		assertTrue(git.permission().containsAll(Set.of(ToolPermission.READ, ToolPermission.WRITE)));
		assertNotNull(registry.getExecutor("git"));
	}

	@Test
	void shouldListAllRegisteredTools() {
		List<ToolDefinition> tools = registry.listTools();

		assertEquals(5, tools.size());
		assertTrue(tools.stream().map(ToolDefinition::toolId).toList()
			.containsAll(List.of("filesystem", "git", "browser", "docker", "terminal")));
	}

	@Test
	void shouldFindToolsByType() {
		List<ToolDefinition> gitTools = registry.findByType(ToolType.GIT);

		assertEquals(1, gitTools.size());
		assertEquals("git", gitTools.get(0).toolId());
		assertTrue(registry.findByType(ToolType.DATABASE).isEmpty());
	}

	@Test
	void shouldRejectDuplicateRegistration() {
		InMemoryToolRegistry local = new InMemoryToolRegistry(List.of());
		local.register(new ToolDefinition("dup", "Dup", ToolType.GIT, "desc", Map.of(),
			Set.of(ToolPermission.READ)), mock(McpToolExecutor.class));

		assertThrows(IllegalStateException.class, () -> local.register(
			new ToolDefinition("dup", "Dup2", ToolType.GIT, "desc", Map.of(),
				Set.of(ToolPermission.READ)), mock(McpToolExecutor.class)));
	}
}
