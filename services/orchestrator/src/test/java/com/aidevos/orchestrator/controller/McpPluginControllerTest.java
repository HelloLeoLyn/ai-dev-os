package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.mcpplugin.McpPlugin;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.mcpplugin.McpPluginTool;
import com.aidevos.orchestrator.tool.ToolAccess;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class McpPluginControllerTest {

	@Test
	void shouldListPlugins() throws Exception {
		McpPluginRegistryService registry = mock(McpPluginRegistryService.class);
		when(registry.listPlugins()).thenReturn(List.of(plugin()));
		MockMvc mockMvc = standaloneSetup(new McpPluginController(registry)).build();

		mockMvc.perform(get("/api/mcp/plugins"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].pluginId").value("filesystem"))
			.andExpect(jsonPath("$[0].enabled").value(true))
			.andExpect(jsonPath("$[0].tools[0].name").value("read_file"));
	}

	@Test
	void shouldGetPluginDetail() throws Exception {
		McpPluginRegistryService registry = mock(McpPluginRegistryService.class);
		when(registry.getPlugin("filesystem")).thenReturn(java.util.Optional.of(plugin()));
		MockMvc mockMvc = standaloneSetup(new McpPluginController(registry)).build();

		mockMvc.perform(get("/api/mcp/plugins/filesystem"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pluginId").value("filesystem"))
			.andExpect(jsonPath("$.permissionLevel").value("read-only"))
			.andExpect(jsonPath("$.tools[0].access").value("READ_ONLY"));
	}

	@Test
	void shouldReturn404WhenPluginMissing() throws Exception {
		McpPluginRegistryService registry = mock(McpPluginRegistryService.class);
		when(registry.getPlugin("missing")).thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new McpPluginController(registry)).build();

		mockMvc.perform(get("/api/mcp/plugins/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldEnablePlugin() throws Exception {
		McpPluginRegistryService registry = mock(McpPluginRegistryService.class);
		McpPlugin plugin = plugin();
		plugin.enable();
		when(registry.enable("filesystem")).thenReturn(java.util.Optional.of(plugin));
		MockMvc mockMvc = standaloneSetup(new McpPluginController(registry)).build();

		mockMvc.perform(post("/api/mcp/plugins/filesystem/enable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void shouldDisablePlugin() throws Exception {
		McpPluginRegistryService registry = mock(McpPluginRegistryService.class);
		McpPlugin plugin = plugin();
		plugin.disable();
		when(registry.disable("filesystem")).thenReturn(java.util.Optional.of(plugin));
		MockMvc mockMvc = standaloneSetup(new McpPluginController(registry)).build();

		mockMvc.perform(post("/api/mcp/plugins/filesystem/disable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(false));
	}

	@Test
	void shouldReturn404WhenTogglingMissingPlugin() throws Exception {
		McpPluginRegistryService registry = mock(McpPluginRegistryService.class);
		when(registry.enable("missing")).thenReturn(java.util.Optional.empty());
		when(registry.disable("missing")).thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new McpPluginController(registry)).build();

		mockMvc.perform(post("/api/mcp/plugins/missing/enable"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/mcp/plugins/missing/disable"))
			.andExpect(status().isNotFound());
	}

	private McpPlugin plugin() {
		return new McpPlugin("filesystem", "Filesystem", "fs", "文件系统工具",
			McpPlugin.PERMISSION_READ_ONLY, true,
			List.of(new McpPluginTool("read_file", "读取文件", ToolAccess.READ_ONLY, false)));
	}
}
