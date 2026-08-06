package com.aidevos.orchestrator.agentmarket;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPluginConfigLoader;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.skill.SkillConfigLoader;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRegistryServiceTest {

	private AgentManager agentManager;
	private SkillRegistryService skillRegistryService;
	private AgentRegistryService service;

	@BeforeEach
	void setUp() {
		agentManager = new AgentManager();
		skillRegistryService = new SkillRegistryService(new SkillConfigLoader(), agentManager);
		McpPluginRegistryService pluginRegistry = new McpPluginRegistryService(
			new McpPluginConfigLoader());
		service = new AgentRegistryService(new AgentMarketConfigLoader(), agentManager,
			skillRegistryService, pluginRegistry);
	}

	@Test
	void shouldLoadConfiguredPackages() {
		List<AgentPackage> packages = service.listPackages();

		assertEquals(List.of("analyst-agent", "browser-agent", "coder-agent", "tester-agent"),
			packages.stream().map(AgentPackage::getAgentId).toList());
		assertTrue(packages.stream().noneMatch(AgentPackage::isInstalled));
	}

	@Test
	void shouldGetPackageById() {
		AgentPackage coder = service.getPackage("coder-agent").orElseThrow();

		assertEquals("Coder Agent", coder.getName());
		assertEquals("1.0.0", coder.getVersion());
		assertEquals("AI Dev OS Team", coder.getAuthor());
		assertEquals("codex", coder.getExecutor());
		assertEquals(List.of("coding", "git"), coder.getCapabilities());
		assertEquals(List.of("coding-skill"), coder.getSkills());
		assertEquals(List.of("filesystem", "git"), coder.getPlugins());
		assertTrue(coder.isEnabled());
		assertFalse(coder.isInstalled());
	}

	@Test
	void shouldReturnEmptyForUnknownPackage() {
		assertTrue(service.getPackage("missing").isEmpty());
	}

	@Test
	void shouldInstallPackageRegistersAgentDefinition() {
		service.install("coder-agent");

		AgentDefinition definition = agentManager.getAgent("coder-agent");
		assertEquals("coder-agent", definition.getName());
		assertEquals("codex", definition.getExecutor());
		assertEquals(List.of("coding", "git"), definition.getCapabilities());
		assertEquals(List.of("coding-skill"), definition.getSkillIds());
		assertEquals("market", definition.getType());
		assertTrue(definition.isEnabled());
		assertTrue(service.getPackage("coder-agent").orElseThrow().isInstalled());
	}

	@Test
	void shouldInstallPackageBindSkills() {
		service.install("tester-agent");

		List<String> skills = skillRegistryService.getSkillsForAgent("tester-agent")
			.stream().map(skill -> skill.getSkillId()).toList();
		assertEquals(List.of("testing-skill"), skills);
	}

	@Test
	void shouldInstallRejectMissingPackage() {
		assertThrows(IllegalArgumentException.class, () -> service.install("missing"));
	}

	@Test
	void shouldInstallRejectUnknownSkill() {
		AgentPackage packageWithUnknownSkill = new AgentPackage("custom-agent", "Custom",
			"1.0.0", null, null, List.of("coding"), List.of("missing-skill"), List.of(),
			"mock", null, true, false);
		service.register(packageWithUnknownSkill);

		assertThrows(IllegalArgumentException.class, () -> service.install("custom-agent"));
	}

	@Test
	void shouldInstallRejectUnknownPlugin() {
		AgentPackage packageWithUnknownPlugin = new AgentPackage("custom-agent", "Custom",
			"1.0.0", null, null, List.of("coding"), List.of(), List.of("missing-plugin"),
			"mock", null, true, false);
		service.register(packageWithUnknownPlugin);

		assertThrows(IllegalArgumentException.class, () -> service.install("custom-agent"));
	}

	@Test
	void shouldUninstallPackageRemovesAgentDefinition() {
		service.install("browser-agent");
		assertTrue(service.getPackage("browser-agent").orElseThrow().isInstalled());

		service.uninstall("browser-agent");

		assertNull(agentManager.getAgent("browser-agent"));
		assertFalse(service.getPackage("browser-agent").orElseThrow().isInstalled());
	}

	@Test
	void shouldUninstallNotInstalledPackageIsNoOp() {
		service.uninstall("coder-agent");

		assertNull(agentManager.getAgent("coder-agent"));
		assertFalse(service.getPackage("coder-agent").orElseThrow().isInstalled());
	}

	@Test
	void shouldUpdateVersion() {
		service.install("coder-agent");

		AgentPackage updated = service.updateVersion("coder-agent", "2.0.0");

		assertEquals("2.0.0", updated.getVersion());
		assertEquals("2.0.0", service.getPackage("coder-agent").orElseThrow().getVersion());
		assertTrue(agentManager.getAgent("coder-agent") != null);
	}

	@Test
	void shouldRejectUpdateWithoutVersion() {
		assertThrows(IllegalArgumentException.class, () -> service.updateVersion("coder-agent", " "));
	}

	@Test
	void shouldRegisterPackageProgrammatically() {
		AgentPackage custom = new AgentPackage("custom-agent", "Custom", "1.0.0", null,
			null, List.of("analysis"), List.of(), List.of(), "mock", null, true, false);

		service.register(custom);

		assertEquals(Optional.of(custom), service.getPackage("custom-agent"));
	}

	@Test
	void shouldRejectDuplicateRegistration() {
		AgentPackage duplicate = new AgentPackage("coder-agent", "Dup", "1.0.0", null,
			null, List.of(), List.of(), List.of(), "mock", null, true, false);

		assertThrows(IllegalArgumentException.class, () -> service.register(duplicate));
	}

	@Test
	void shouldRejectRegistrationWithoutId() {
		AgentPackage missingId = new AgentPackage(null, "NoId", "1.0.0", null, null,
			List.of(), List.of(), List.of(), "mock", null, true, false);

		assertThrows(IllegalArgumentException.class, () -> service.register(missingId));
	}
}
