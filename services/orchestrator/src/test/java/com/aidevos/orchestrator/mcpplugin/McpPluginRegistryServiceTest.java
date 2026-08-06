package com.aidevos.orchestrator.mcpplugin;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.tool.ToolAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpPluginRegistryServiceTest {

	private McpPluginRegistryService service;

	@BeforeEach
	void setUp() {
		service = new McpPluginRegistryService(new McpPluginConfigLoader());
	}

	@Test
	void shouldLoadConfiguredPlugins() {
		List<McpPlugin> plugins = service.listPlugins();

		assertEquals(List.of("browser", "docker", "filesystem", "git"),
			plugins.stream().map(McpPlugin::getPluginId).toList());
	}

	@Test
	void shouldGetPluginById() {
		McpPlugin filesystem = service.getPlugin("filesystem").orElseThrow();

		assertEquals("Filesystem", filesystem.getName());
		assertEquals("fs", filesystem.getType());
		assertTrue(filesystem.isEnabled());
		assertEquals(McpPlugin.PERMISSION_READ_ONLY, filesystem.getPermissionLevel());
	}

	@Test
	void shouldReturnEmptyForUnknownPlugin() {
		assertTrue(service.getPlugin("missing").isEmpty());
		assertTrue(service.getTools("missing").isEmpty());
	}

	@Test
	void shouldEnableAndDisablePlugin() {
		assertTrue(service.disable("git").orElseThrow().isEnabled() == false);
		assertFalse(service.getPlugin("git").orElseThrow().isEnabled());

		assertTrue(service.enable("git").orElseThrow().isEnabled());
		assertTrue(service.getPlugin("git").orElseThrow().isEnabled());
	}

	@Test
	void shouldReturnEmptyWhenTogglingUnknownPlugin() {
		assertTrue(service.enable("missing").isEmpty());
		assertTrue(service.disable("missing").isEmpty());
	}

	@Test
	void shouldListPluginTools() {
		List<McpPluginTool> tools = service.getTools("filesystem");

		assertEquals(List.of("read_file", "read_directory", "write_file", "delete_file"),
			tools.stream().map(McpPluginTool::name).toList());
		assertEquals(ToolAccess.READ_ONLY, tools.getFirst().access());
		assertFalse(tools.getFirst().dangerous());
		assertTrue(tools.get(2).dangerous());
		assertEquals(ToolAccess.WORKSPACE_WRITE, tools.get(2).access());
	}

	@Test
	void shouldMarkDockerAsDangerousPermissionLevel() {
		McpPlugin docker = service.getPlugin("docker").orElseThrow();

		assertEquals(McpPlugin.PERMISSION_WORKSPACE_WRITE, docker.getPermissionLevel());
		assertTrue(docker.getTools().stream().anyMatch(McpPluginTool::dangerous));
	}

	@Test
	void shouldRegisterPluginProgrammatically() {
		McpPlugin plugin = new McpPlugin("custom", "Custom", "custom", "custom plugin",
			McpPlugin.PERMISSION_READ_ONLY, true,
			List.of(new McpPluginTool("ping", "ping tool", ToolAccess.READ_ONLY, false)));

		service.register(plugin);

		assertEquals(Optional.of(plugin), service.getPlugin("custom"));
		assertEquals(1, service.getTools("custom").size());
	}

	@Test
	void shouldRejectDuplicateRegistration() {
		McpPlugin plugin = new McpPlugin("filesystem", "Filesystem", "fs", "dup", null, true,
			List.of());

		assertThrows(IllegalArgumentException.class, () -> service.register(plugin));
	}

	@Test
	void shouldRejectRegistrationWithoutId() {
		McpPlugin plugin = new McpPlugin(null, "NoId", "custom", "missing id", null, true,
			List.of());

		assertThrows(IllegalArgumentException.class, () -> service.register(plugin));
	}
}
