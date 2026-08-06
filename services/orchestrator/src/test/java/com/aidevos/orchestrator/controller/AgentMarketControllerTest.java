package com.aidevos.orchestrator.controller;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agentmarket.AgentPackage;
import com.aidevos.orchestrator.agentmarket.AgentRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AgentMarketControllerTest {

	@Test
	void shouldListPackages() throws Exception {
		AgentRegistryService registry = mock(AgentRegistryService.class);
		when(registry.listPackages()).thenReturn(List.of(package_()));
		MockMvc mockMvc = standaloneSetup(new AgentMarketController(registry)).build();

		mockMvc.perform(get("/api/agent-market"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].agentId").value("coder-agent"))
			.andExpect(jsonPath("$[0].version").value("1.0.0"))
			.andExpect(jsonPath("$[0].capabilities[0]").value("coding"))
			.andExpect(jsonPath("$[0].skills[0]").value("coding-skill"))
			.andExpect(jsonPath("$[0].installed").value(false));
	}

	@Test
	void shouldGetPackageDetail() throws Exception {
		AgentRegistryService registry = mock(AgentRegistryService.class);
		when(registry.getPackage("coder-agent")).thenReturn(Optional.of(package_()));
		MockMvc mockMvc = standaloneSetup(new AgentMarketController(registry)).build();

		mockMvc.perform(get("/api/agent-market/coder-agent"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agentId").value("coder-agent"))
			.andExpect(jsonPath("$.executor").value("codex"))
			.andExpect(jsonPath("$.plugins[0]").value("filesystem"))
			.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void shouldReturn404WhenPackageMissing() throws Exception {
		AgentRegistryService registry = mock(AgentRegistryService.class);
		when(registry.getPackage("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = standaloneSetup(new AgentMarketController(registry)).build();

		mockMvc.perform(get("/api/agent-market/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldInstallPackage() throws Exception {
		AgentRegistryService registry = mock(AgentRegistryService.class);
		AgentPackage agentPackage = package_();
		agentPackage.markInstalled();
		when(registry.getPackage("coder-agent")).thenReturn(Optional.of(package_()));
		when(registry.install("coder-agent")).thenReturn(agentPackage);
		MockMvc mockMvc = standaloneSetup(new AgentMarketController(registry)).build();

		mockMvc.perform(post("/api/agent-market/coder-agent/install"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.installed").value(true));
	}

	@Test
	void shouldUninstallPackage() throws Exception {
		AgentRegistryService registry = mock(AgentRegistryService.class);
		AgentPackage agentPackage = package_();
		agentPackage.markInstalled();
		agentPackage.markUninstalled();
		when(registry.getPackage("coder-agent")).thenReturn(Optional.of(package_()));
		when(registry.uninstall("coder-agent")).thenReturn(agentPackage);
		MockMvc mockMvc = standaloneSetup(new AgentMarketController(registry)).build();

		mockMvc.perform(post("/api/agent-market/coder-agent/uninstall"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.installed").value(false));
	}

	@Test
	void shouldReturn404WhenInstallingMissingPackage() throws Exception {
		AgentRegistryService registry = mock(AgentRegistryService.class);
		when(registry.getPackage("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = standaloneSetup(new AgentMarketController(registry)).build();

		mockMvc.perform(post("/api/agent-market/missing/install"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/agent-market/missing/uninstall"))
			.andExpect(status().isNotFound());
	}

	private AgentPackage package_() {
		return new AgentPackage("coder-agent", "Coder Agent", "1.0.0", "代码 Agent",
			"AI Dev OS Team", List.of("coding", "git"), List.of("coding-skill"),
			List.of("filesystem", "git"), "codex", null, true, false);
	}
}
