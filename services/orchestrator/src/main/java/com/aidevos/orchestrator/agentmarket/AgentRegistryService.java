package com.aidevos.orchestrator.agentmarket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent market registry: hosts the agent package catalog and manages the
 * install lifecycle. Installing a package registers an AgentDefinition with
 * the existing AgentManager, binds its skills through SkillRegistryService and
 * validates its MCP plugins against McpPluginRegistryService. The execution
 * engine, scheduler and worker are not modified.
 */
@Service("agentMarketRegistryService")
public class AgentRegistryService {

	private final Map<String, AgentPackage> packages = new ConcurrentHashMap<>();
	private final AgentManager agentManager;
	private final SkillRegistryService skillRegistryService;
	private final McpPluginRegistryService mcpPluginRegistryService;
	private final AgentPackageRepository repository;

	public AgentRegistryService(AgentMarketConfigLoader configLoader,
			AgentManager agentManager, SkillRegistryService skillRegistryService,
			McpPluginRegistryService mcpPluginRegistryService) {
		this(configLoader, agentManager, skillRegistryService, mcpPluginRegistryService,
			new InMemoryAgentPackageRepository());
	}

	@Autowired
	public AgentRegistryService(AgentMarketConfigLoader configLoader,
			AgentManager agentManager, SkillRegistryService skillRegistryService,
			McpPluginRegistryService mcpPluginRegistryService,
			AgentPackageRepository repository) {
		this.agentManager = agentManager;
		this.skillRegistryService = skillRegistryService;
		this.mcpPluginRegistryService = mcpPluginRegistryService;
		this.repository = repository;
		configLoader.loadPackages().forEach(agentPackage -> register(restore(agentPackage)));
	}

	public AgentPackage register(AgentPackage agentPackage) {
		if (agentPackage == null || isBlank(agentPackage.getAgentId())) {
			throw new IllegalArgumentException("Agent id is required");
		}
		AgentPackage previous = packages.putIfAbsent(agentPackage.getAgentId(), agentPackage);
		if (previous != null) {
			throw new IllegalArgumentException("Agent package already registered: "
				+ agentPackage.getAgentId());
		}
		repository.save(agentPackage);
		return agentPackage;
	}

	public List<AgentPackage> listPackages() {
		List<AgentPackage> result = new ArrayList<>(packages.values());
		result.sort(Comparator.comparing(AgentPackage::getAgentId));
		return result;
	}

	public Optional<AgentPackage> getPackage(String agentId) {
		if (isBlank(agentId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(packages.get(agentId));
	}

	/**
	 * Installs a package: registers the AgentDefinition with AgentManager,
	 * binds the declared skills and validates the declared MCP plugins.
	 * Re-installing an installed package re-registers the agent (update).
	 */
	public AgentPackage install(String agentId) {
		AgentPackage agentPackage = requirePackage(agentId);
		validateSkills(agentPackage);
		validatePlugins(agentPackage);
		agentManager.register(toDefinition(agentPackage));
		agentPackage.markInstalled();
		repository.save(agentPackage);
		return agentPackage;
	}

	/**
	 * Uninstalls a package: removes the registered AgentDefinition from
	 * AgentManager. No-op when the package is not installed.
	 */
	public AgentPackage uninstall(String agentId) {
		AgentPackage agentPackage = requirePackage(agentId);
		if (agentPackage.isInstalled()) {
			agentManager.removeAgent(agentId);
			agentPackage.markUninstalled();
			repository.save(agentPackage);
		}
		return agentPackage;
	}

	/**
	 * Updates a package version; when the package is installed the registered
	 * AgentDefinition is re-registered to stay in sync.
	 */
	public AgentPackage updateVersion(String agentId, String newVersion) {
		if (isBlank(newVersion)) {
			throw new IllegalArgumentException("Version is required");
		}
		AgentPackage agentPackage = requirePackage(agentId);
		agentPackage.updateVersion(newVersion.trim());
		if (agentPackage.isInstalled()) {
			agentManager.register(toDefinition(agentPackage));
		}
		repository.save(agentPackage);
		return agentPackage;
	}

	private AgentPackage restore(AgentPackage agentPackage) {
		AgentPackage persisted = repository.get(agentPackage.getAgentId());
		if (persisted == null) {
			return agentPackage;
		}
		return new AgentPackage(agentPackage.getAgentId(), agentPackage.getName(),
			persisted.getVersion() != null ? persisted.getVersion() : agentPackage.getVersion(),
			agentPackage.getDescription(), agentPackage.getAuthor(),
			agentPackage.getCapabilities(), agentPackage.getSkills(), agentPackage.getPlugins(),
			agentPackage.getExecutor(), agentPackage.getExecutorConfig(),
			persisted.isEnabled(), persisted.isInstalled());
	}

	private AgentPackage requirePackage(String agentId) {
		return getPackage(agentId)
			.orElseThrow(() -> new IllegalArgumentException("Agent package not found: " + agentId));
	}

	private void validatePlugins(AgentPackage agentPackage) {
		for (String pluginId : agentPackage.getPlugins()) {
			if (mcpPluginRegistryService.getPlugin(pluginId).isEmpty()) {
				throw new IllegalArgumentException("Plugin not found: " + pluginId
					+ " for agent package: " + agentPackage.getAgentId());
			}
		}
	}

	private void validateSkills(AgentPackage agentPackage) {
		for (String skillId : agentPackage.getSkills()) {
			if (skillRegistryService.getSkill(skillId).isEmpty()) {
				throw new IllegalArgumentException("Skill not found: " + skillId
					+ " for agent package: " + agentPackage.getAgentId());
			}
		}
	}

	private AgentDefinition toDefinition(AgentPackage agentPackage) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(agentPackage.getAgentId());
		definition.setExecutor(agentPackage.getExecutor());
		definition.setExecutorConfig(agentPackage.getExecutorConfig());
		definition.setCapabilities(agentPackage.getCapabilities());
		definition.setSkillIds(agentPackage.getSkills());
		definition.setDescription(agentPackage.getDescription());
		definition.setType("market");
		definition.setPermissionLevel("standard");
		definition.setEnabled(agentPackage.isEnabled());
		return definition;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
