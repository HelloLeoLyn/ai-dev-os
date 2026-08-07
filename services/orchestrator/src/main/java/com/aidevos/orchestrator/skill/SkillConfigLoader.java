package com.aidevos.orchestrator.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.common.config.YamlConfigSupport;
import org.springframework.stereotype.Component;

/**
 * Loads skills.yaml into Skill definitions. YAML reading, conversions and
 * validation helpers come from YamlConfigSupport; read-only configuration used
 * by the skill registry.
 */
@Component
public class SkillConfigLoader {

	private static final String CONFIG_FILE = "skills.yaml";

	public List<Skill> loadSkills() {
		return toSkills(YamlConfigSupport.load(CONFIG_FILE).get("skills"));
	}

	List<Skill> toSkills(Object value) {
		List<Skill> skills = new ArrayList<>();
		Set<String> skillIds = YamlConfigSupport.newIdentitySet();
		for (Map<String, Object> map : YamlConfigSupport.asList(value, "skills", "skill")) {
			Skill skill = toSkill(map);
			YamlConfigSupport.require(skill.getSkillId(), "skillId");
			YamlConfigSupport.requireUnique(skillIds, skill.getSkillId(), "skillId");
			skills.add(skill);
		}
		return skills;
	}

	private Skill toSkill(Map<String, Object> map) {
		String typeValue = YamlConfigSupport.string(map, "type");
		SkillType type = SkillType.ANALYSIS;
		if (!YamlConfigSupport.isBlank(typeValue)) {
			try {
				type = SkillType.valueOf(typeValue);
			}
			catch (IllegalArgumentException exception) {
				throw new IllegalStateException("Invalid skill type: " + typeValue);
			}
		}
		boolean enabled = YamlConfigSupport.bool(map, "enabled", true);
		return new Skill(YamlConfigSupport.string(map, "skillId"),
			YamlConfigSupport.string(map, "name"), YamlConfigSupport.string(map, "description"),
			type, YamlConfigSupport.string(map, "version"), enabled,
			YamlConfigSupport.stringList(map.get("tools"), "skill tools", List.of()),
			YamlConfigSupport.string(map, "instructions"));
	}
}
