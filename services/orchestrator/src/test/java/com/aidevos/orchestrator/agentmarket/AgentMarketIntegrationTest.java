package com.aidevos.orchestrator.agentmarket;

import java.util.List;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPluginConfigLoader;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.skill.SkillConfigLoader;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that installing a market agent package syncs version and
 * capabilities into the registered AgentDefinition so market agents can
 * participate in dynamic capability-based execution.
 */
class AgentMarketIntegrationTest {

	private AgentManager agentManager;
	private AgentRegistryService market;
	private AgentCapabilityResolver resolver;

	@BeforeEach
	void setUp() {
		agentManager = new AgentManager();
		SkillRegistryService skillRegistry = new SkillRegistryService(
			new SkillConfigLoader(), agentManager);
		McpPluginRegistryService pluginRegistry = new McpPluginRegistryService(
			new McpPluginConfigLoader());
		market = new AgentRegistryService(new AgentMarketConfigLoader(), agentManager,
			skillRegistry, pluginRegistry);
		resolver = new AgentCapabilityResolver(agentManager);
	}

	@Test
	void shouldSyncVersionAndCapabilitiesOnInstall() {
		market.install("coder-agent");

		AgentDefinition definition = agentManager.getAgent("coder-agent");
		assertEquals("1.0.0", definition.getVersion());
		assertEquals(List.of("coding", "git"), definition.getCapabilities());
		assertEquals("market", definition.getType());
	}

	@Test
	void shouldLetMarketAgentParticipateInCapabilitySelection() {
		market.install("coder-agent");

		assertEquals("coder-agent", resolver.resolveAgent("coding").orElseThrow().getName());
	}

	@Test
	void shouldUseMarketVersionForBrowserSelection() {
		market.install("browser-agent");

		AgentDefinition selected = resolver.resolveAgent("browser").orElseThrow();
		assertEquals("browser-agent", selected.getName());
		assertEquals("1.1.0", selected.getVersion());
	}

	@Test
	void shouldSyncVersionOnPackageUpdate() {
		market.install("coder-agent");
		market.updateVersion("coder-agent", "2.0.0");

		assertEquals("2.0.0", agentManager.getAgent("coder-agent").getVersion());
		assertEquals("coder-agent", resolver.resolveAgent("coding").orElseThrow().getName());
		assertEquals("2.0.0", resolver.resolveAgent("coding").orElseThrow().getVersion());
	}

	@Test
	void shouldResolveTestingToMarketTesterAgent() {
		market.install("tester-agent");

		AgentDefinition selected = resolver.resolveAgent("testing").orElseThrow();
		assertEquals("tester-agent", selected.getName());
		assertEquals(List.of("testing", "browser"), selected.getCapabilities());
	}

	@Test
	void shouldPreferRecentlyInstalledMarketAgentOverConfigCandidate() {
		AgentDefinition configCoder = new AgentDefinition();
		configCoder.setName("coder");
		configCoder.setVersion("1.0.0");
		configCoder.setCapabilities(List.of("coding", "git"));
		configCoder.setExecutor("codex");
		agentManager.register(configCoder);

		market.install("coder-agent");

		assertEquals("coder-agent", resolver.resolveAgent("coding").orElseThrow().getName());
	}
}
