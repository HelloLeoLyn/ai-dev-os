package com.aidevos.orchestrator.config;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConfigLoaderTest {

	@Test
	void shouldLoadAgentsFromYaml() {
		AgentConfigLoader agentConfigLoader = new AgentConfigLoader();

		List<AgentDefinition> agents = agentConfigLoader.loadAgents();

		assertEquals(7, agents.size());
		assertAgent(agents.get(0), "planner", "mock", List.of("planning", "analysis"));
		assertAgent(agents.get(1), "analyst", "codex", List.of("analysis", "read-only"));
		assertAgent(agents.get(2), "executor", "mock", List.of("coding", "git"));
		assertAgent(agents.get(3), "coder", "codex", List.of("coding", "git"));
		assertAgent(agents.get(4), "tester", "openclaw", List.of("testing", "browser"));
		assertAgent(agents.get(5), "browser-agent", "openclaw", List.of("browser"));
		assertAgent(agents.get(6), "mcp-reader", "tool", List.of("tool", "read-only"));
		assertEquals("main", agents.get(4).getExecutorConfig().get("agentId"));
		assertEquals("main", agents.get(5).getExecutorConfig().get("agentId"));
		assertEquals(null, agents.get(3).getExecutorConfig().get("workspace"));
		assertEquals("deepseek-v4-flash", agents.get(3).getExecutorConfig().get("model"));
		assertEquals("1.0.0", agents.get(3).getVersion());
		assertEquals("1.0.0", agents.get(0).getVersion());
		assertEquals("Executes coding tasks", agents.get(3).getDescription());
		assertEquals("system", agents.get(3).getType());
		assertEquals("standard", agents.get(3).getPermissionLevel());
		assertEquals(true, agents.get(3).isEnabled());
		assertEquals("read-only", agents.get(1).getPermissionLevel());
		assertEquals("read-only", agents.get(6).getPermissionLevel());
	}

	/** V1-FINAL-CLOSEOUT：analyst 默认模型与 coder 一致，且能被 ModelResolver 正确解析 */
	@Test
	void analystDefaultModelIsConfiguredAndResolvable() {
		AgentConfigLoader agentConfigLoader = new AgentConfigLoader();
		AgentDefinition analyst = agentConfigLoader.loadAgents().stream()
			.filter(agent -> "analyst".equals(agent.getName()))
			.findFirst().orElseThrow();
		Object analystModel = analyst.getExecutorConfig().get("model");
		assertEquals("deepseek-v4-flash", analystModel,
			"analyst 必须配置有效默认模型（与 coder 一致）");

		// 用真实 ModelResolver 验证 default model 可解析（analyst 是 codex executor）
		com.aidevos.orchestrator.modelregistry.InMemoryProviderDefinitionRepository providers =
			new com.aidevos.orchestrator.modelregistry.InMemoryProviderDefinitionRepository();
		com.aidevos.orchestrator.modelregistry.InMemoryModelDefinitionRepository models =
			new com.aidevos.orchestrator.modelregistry.InMemoryModelDefinitionRepository();
		com.aidevos.orchestrator.modelregistry.ProviderDefinition provider =
			new com.aidevos.orchestrator.modelregistry.ProviderDefinition();
		provider.setProviderId("deepseek");
		provider.setDisplayName("DeepSeek");
		provider.setBaseUrl("https://api.deepseek.com");
		provider.setCredentialRef("DEEPSEEK_API_KEY");
		provider.setEnabled(true);
		providers.save(provider);
		com.aidevos.orchestrator.modelregistry.ModelDefinition model =
			new com.aidevos.orchestrator.modelregistry.ModelDefinition();
		model.setModelId("deepseek-v4-flash");
		model.setDisplayName("DeepSeek V4 Flash");
		model.setProviderId("deepseek");
		model.setExecutorType("codex");
		model.setEnabled(true);
		models.save(model);
		com.aidevos.orchestrator.modelregistry.ModelResolver resolver =
			new com.aidevos.orchestrator.modelregistry.ModelResolver(providers, models) {
				@Override
				protected String lookupEnv(String name) {
					return "test-value";
				}
			};

		com.aidevos.orchestrator.modelregistry.ResolvedModel resolved =
			resolver.resolve(null, String.valueOf(analystModel));

		assertEquals("deepseek-v4-flash", resolved.resolvedModelId());
		assertEquals("codex", resolved.executorType());
	}

	private void assertAgent(AgentDefinition agent, String name, String executor, List<String> capabilities) {
		assertEquals(name, agent.getName());
		assertEquals(executor, agent.getExecutor());
		assertEquals(capabilities, agent.getCapabilities());
	}
}
