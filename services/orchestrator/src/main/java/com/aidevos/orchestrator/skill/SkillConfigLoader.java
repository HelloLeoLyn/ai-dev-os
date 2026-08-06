package com.aidevos.orchestrator.skill;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads skills.yaml into Skill definitions. Mirrors McpPluginConfigLoader;
 * read-only configuration used by the skill registry.
 */
@Component
public class SkillConfigLoader {

	private static final String CONFIG_FILE = "skills.yaml";

	public List<Skill> loadSkills() {
		try (InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream(CONFIG_FILE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Configuration file not found: " + CONFIG_FILE);
			}
			Map<String, Object> config = new Yaml().load(inputStream);
			return toSkills(config.get("skills"));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to read configuration file: " + CONFIG_FILE,
				exception);
		}
	}

	List<Skill> toSkills(Object value) {
		if (!(value instanceof List<?> skillValues)) {
			throw new IllegalStateException("Invalid skills configuration");
		}
		List<Skill> skills = new ArrayList<>();
		Set<String> skillIds = new HashSet<>();
		for (Object skillValue : skillValues) {
			if (!(skillValue instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Invalid skill definition");
			}
			Skill skill = toSkill(map);
			if (isBlank(skill.getSkillId())) {
				throw new IllegalStateException("skillId is required");
			}
			if (!skillIds.add(skill.getSkillId())) {
				throw new IllegalStateException("Duplicate skillId: " + skill.getSkillId());
			}
			skills.add(skill);
		}
		return skills;
	}

	private Skill toSkill(Map<?, ?> map) {
		String typeValue = string(map, "type");
		SkillType type = SkillType.ANALYSIS;
		if (typeValue != null && !typeValue.isBlank()) {
			try {
				type = SkillType.valueOf(typeValue);
			}
			catch (IllegalArgumentException exception) {
				throw new IllegalStateException("Invalid skill type: " + typeValue);
			}
		}
		boolean enabled = !map.containsKey("enabled") || Boolean.TRUE.equals(map.get("enabled"));
		return new Skill(string(map, "skillId"), string(map, "name"),
			string(map, "description"), type, string(map, "version"), enabled,
			toStringList(map.get("tools")), string(map, "instructions"));
	}

	private List<String> toStringList(Object value) {
		if (!(value instanceof List<?> toolValues)) {
			return List.of();
		}
		List<String> tools = new ArrayList<>();
		for (Object toolValue : toolValues) {
			if (!(toolValue instanceof String tool)) {
				throw new IllegalStateException("Invalid skill tools configuration");
			}
			tools.add(tool);
		}
		return tools;
	}

	private String string(Map<?, ?> map, String key) {
		Object value = map.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
