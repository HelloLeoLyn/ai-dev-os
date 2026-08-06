package com.aidevos.orchestrator.agentmarket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A distributable agent package from the agent market. Installed packages are
 * turned into AgentDefinition instances registered with the existing
 * AgentManager; the package declares the skills (bound via SkillRegistryService)
 * and MCP plugins (validated against McpPluginRegistryService) the agent uses.
 */
public class AgentPackage {

	private final String agentId;
	private final String name;
	private final String description;
	private final String author;
	private final List<String> capabilities;
	private final List<String> skills;
	private final List<String> plugins;
	private final String executor;
	private final Map<String, Object> executorConfig;
	private volatile String version;
	private volatile boolean enabled;
	private volatile boolean installed;

	public AgentPackage(String agentId, String name, String version, String description,
			String author, List<String> capabilities, List<String> skills,
			List<String> plugins, String executor, Map<String, Object> executorConfig,
			boolean enabled, boolean installed) {
		this.agentId = agentId;
		this.name = name;
		this.version = version;
		this.description = description;
		this.author = author;
		this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
		this.skills = skills == null ? List.of() : List.copyOf(skills);
		this.plugins = plugins == null ? List.of() : List.copyOf(plugins);
		this.executor = executor == null || executor.isBlank() ? "mock" : executor;
		this.executorConfig = executorConfig == null
			? new LinkedHashMap<>() : new LinkedHashMap<>(executorConfig);
		this.enabled = enabled;
		this.installed = installed;
	}

	public synchronized void updateVersion(String newVersion) {
		this.version = newVersion;
	}

	public synchronized void markInstalled() {
		this.installed = true;
	}

	public synchronized void markUninstalled() {
		this.installed = false;
	}

	public String getAgentId() {
		return agentId;
	}

	public String getName() {
		return name;
	}

	public String getVersion() {
		return version;
	}

	public String getDescription() {
		return description;
	}

	public String getAuthor() {
		return author;
	}

	public List<String> getCapabilities() {
		return capabilities;
	}

	public List<String> getSkills() {
		return skills;
	}

	public List<String> getPlugins() {
		return plugins;
	}

	public String getExecutor() {
		return executor;
	}

	public Map<String, Object> getExecutorConfig() {
		return executorConfig;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isInstalled() {
		return installed;
	}
}
