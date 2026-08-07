package com.aidevos.orchestrator.agentmarket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.common.config.YamlConfigSupport;
import org.springframework.stereotype.Component;

/**
 * Loads agents-market.yaml into AgentPackage definitions. YAML reading,
 * conversions and validation helpers come from YamlConfigSupport; read-only
 * catalog configuration used by the agent market registry.
 */
@Component
public class AgentMarketConfigLoader {

	private static final String CONFIG_FILE = "agents-market.yaml";

	public List<AgentPackage> loadPackages() {
		return toPackages(YamlConfigSupport.load(CONFIG_FILE).get("agents"));
	}

	List<AgentPackage> toPackages(Object value) {
		List<AgentPackage> packages = new ArrayList<>();
		Set<String> agentIds = YamlConfigSupport.newIdentitySet();
		for (Map<String, Object> map : YamlConfigSupport.asList(value, "agent market",
				"agent package")) {
			AgentPackage agentPackage = toPackage(map);
			YamlConfigSupport.require(agentPackage.getAgentId(), "agentId");
			YamlConfigSupport.requireUnique(agentIds, agentPackage.getAgentId(), "agentId");
			packages.add(agentPackage);
		}
		return packages;
	}

	private AgentPackage toPackage(Map<String, Object> map) {
		boolean enabled = YamlConfigSupport.bool(map, "enabled", true);
		return new AgentPackage(YamlConfigSupport.string(map, "agentId"),
			YamlConfigSupport.string(map, "name"), YamlConfigSupport.string(map, "version"),
			YamlConfigSupport.string(map, "description"), YamlConfigSupport.string(map, "author"),
			YamlConfigSupport.stringList(map.get("capabilities"), "agent package list", List.of()),
			YamlConfigSupport.stringList(map.get("skills"), "agent package list", List.of()),
			YamlConfigSupport.stringList(map.get("plugins"), "agent package list", List.of()),
			YamlConfigSupport.string(map, "executor"),
			YamlConfigSupport.objectMap(map.get("executorConfig"), "executor"), enabled, false);
	}
}
