package com.aidevos.orchestrator.config;

import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
		for (Object agentValue : agents) {
			if (!(agentValue instanceof Map<?, ?> agent)) {
				throw new IllegalStateException("Invalid agent definition");
			}
			agentDefinitions.add(toAgentDefinition(agent));
		}
		return agentDefinitions;
	}

	private AgentDefinition toAgentDefinition(Map<?, ?> agent) {
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName((String) agent.get("name"));
		agentDefinition.setExecutor((String) agent.get("executor"));
		agentDefinition.setType((String) agent.get("type"));
		agentDefinition.setDescription((String) agent.get("description"));
		agentDefinition.setPermissionLevel((String) agent.get("permissionLevel"));
		agentDefinition.setEnabled(Boolean.TRUE.equals(agent.get("enabled")));
		return agentDefinition;
	}
}
