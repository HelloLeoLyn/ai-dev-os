package com.aidevos.orchestrator.config;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentConfigLoader {

	private static final String CONFIG_FILE = "agents.yaml";

	public List<AgentDefinition> loadAgents() {
		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Configuration file not found: " + CONFIG_FILE);
			}

			Map<String, Object> config = new Yaml().load(inputStream);
			return toAgentDefinitions(config);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read configuration file: " + CONFIG_FILE, exception);
		}
	}

	private List<AgentDefinition> toAgentDefinitions(Map<String, Object> config) {
		Object agentsValue = config.get("agents");
		if (!(agentsValue instanceof List<?> agents)) {
			throw new IllegalStateException("Invalid agents configuration");
		}

		List<AgentDefinition> agentDefinitions = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (Object agentValue : agents) {
			if (!(agentValue instanceof Map<?, ?> agent)) {
				throw new IllegalStateException("Invalid agent definition");
			}
			AgentDefinition agentDefinition = toAgentDefinition(agent);
			validate(agentDefinition, names);
			agentDefinitions.add(agentDefinition);
		}
		return agentDefinitions;
	}

	private AgentDefinition toAgentDefinition(Map<?, ?> agent) {
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName((String) agent.get("name"));
		agentDefinition.setExecutor((String) agent.get("executor"));
		agentDefinition.setExecutorConfig(toObjectMap(agent.get(agentDefinition.getExecutor())));
		agentDefinition.setCapabilities(toStringList(agent.get("capabilities")));
		agentDefinition.setSkillIds(toStringList(agent.get("skillIds")));
		agentDefinition.setType((String) agent.get("type"));
		agentDefinition.setDescription((String) agent.get("description"));
		agentDefinition.setPermissionLevel((String) agent.get("permissionLevel"));
		if (agent.containsKey("enabled")) {
			agentDefinition.setEnabled(Boolean.TRUE.equals(agent.get("enabled")));
		}
		return agentDefinition;
	}

	private void validate(AgentDefinition agent, Set<String> names) {
		if (isBlank(agent.getName())) {
			throw new IllegalStateException("Agent name is required");
		}
		if (!names.add(agent.getName())) {
			throw new IllegalStateException("Duplicate agent name: " + agent.getName());
		}
		if (isBlank(agent.getExecutor())) {
			throw new IllegalStateException("Executor is required for agent: " + agent.getName());
		}
		if ("openclaw".equals(agent.getExecutor()) && isBlank(stringConfig(agent, "agentId"))) {
			throw new IllegalStateException("agentId is required for OpenClaw agent: " + agent.getName());
		}
	}

	private String stringConfig(AgentDefinition agent, String key) {
		Object value = agent.getExecutorConfig().get(key);
		return value instanceof String string ? string : null;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private List<String> toStringList(Object value) {
		if (!(value instanceof List<?> values)) {
			return null;
		}

		List<String> strings = new ArrayList<>();
		for (Object item : values) {
			if (!(item instanceof String string)) {
				throw new IllegalStateException("Invalid capabilities configuration");
			}
			strings.add(string);
		}
		return strings;
	}

	private Map<String, Object> toObjectMap(Object value) {
		Map<String, Object> config = new java.util.LinkedHashMap<>();
		if (value == null) {
			return config;
		}
		if (!(value instanceof Map<?, ?> values)) {
			throw new IllegalStateException("Invalid executor configuration");
		}
		for (Map.Entry<?, ?> entry : values.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new IllegalStateException("Invalid executor configuration key");
			}
			config.put(key, entry.getValue());
		}
		return config;
	}
}
