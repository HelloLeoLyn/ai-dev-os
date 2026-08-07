package com.aidevos.orchestrator.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.common.config.YamlConfigSupport;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Component;

/**
 * Loads agents.yaml into AgentDefinition instances. The YAML reading,
 * conversions and validation helpers come from YamlConfigSupport; the mapping
 * and domain rules stay here.
 */
@Component
public class AgentConfigLoader {

	private static final String CONFIG_FILE = "agents.yaml";

	public List<AgentDefinition> loadAgents() {
		return toAgentDefinitions(YamlConfigSupport.load(CONFIG_FILE));
	}

	private List<AgentDefinition> toAgentDefinitions(Map<String, Object> config) {
		List<AgentDefinition> agentDefinitions = new ArrayList<>();
		Set<String> names = YamlConfigSupport.newIdentitySet();
		for (Map<String, Object> agent : YamlConfigSupport.asList(config.get("agents"),
				"agents", "agent")) {
			AgentDefinition agentDefinition = toAgentDefinition(agent);
			validate(agentDefinition, names);
			agentDefinitions.add(agentDefinition);
		}
		return agentDefinitions;
	}

	private AgentDefinition toAgentDefinition(Map<String, Object> agent) {
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName(YamlConfigSupport.string(agent, "name"));
		agentDefinition.setExecutor(YamlConfigSupport.string(agent, "executor"));
		agentDefinition.setExecutorConfig(
			YamlConfigSupport.objectMap(agent.get(agentDefinition.getExecutor()), "executor"));
		agentDefinition.setCapabilities(
			YamlConfigSupport.stringList(agent.get("capabilities"), "capabilities", null));
		agentDefinition.setSkillIds(
			YamlConfigSupport.stringList(agent.get("skillIds"), "skillIds", null));
		agentDefinition.setType(YamlConfigSupport.string(agent, "type"));
		agentDefinition.setDescription(YamlConfigSupport.string(agent, "description"));
		agentDefinition.setVersion(YamlConfigSupport.string(agent, "version"));
		agentDefinition.setPermissionLevel(YamlConfigSupport.string(agent, "permissionLevel"));
		agentDefinition.setEnabled(YamlConfigSupport.bool(agent, "enabled", true));
		return agentDefinition;
	}

	private void validate(AgentDefinition agent, Set<String> names) {
		YamlConfigSupport.require(agent.getName(), "Agent name");
		YamlConfigSupport.requireUnique(names, agent.getName(), "agent name");
		if (YamlConfigSupport.isBlank(agent.getExecutor())) {
			throw new IllegalStateException("Executor is required for agent: " + agent.getName());
		}
		if ("openclaw".equals(agent.getExecutor())
				&& YamlConfigSupport.isBlank(
					YamlConfigSupport.string(agent.getExecutorConfig(), "agentId"))) {
			throw new IllegalStateException(
				"agentId is required for OpenClaw agent: " + agent.getName());
		}
	}
}
