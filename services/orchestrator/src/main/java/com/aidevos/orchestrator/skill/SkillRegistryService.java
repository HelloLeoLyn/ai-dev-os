package com.aidevos.orchestrator.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.springframework.stereotype.Service;

/**
 * Skill registry: registers skills (from skills.yaml or programmatically),
 * queries skills, toggles the enabled state and resolves the skills available
 * to an agent. Agents bind skills explicitly through skillIds; when no binding
 * is configured the skill type is matched against the agent capabilities.
 * Management layer only - the execution engine, scheduler and worker are not
 * touched.
 */
@Service
public class SkillRegistryService {

	private final Map<String, Skill> skills = new ConcurrentHashMap<>();
	private final AgentManager agentManager;

	public SkillRegistryService(SkillConfigLoader configLoader, AgentManager agentManager) {
		this.agentManager = agentManager;
		configLoader.loadSkills().forEach(this::register);
	}

	public Skill register(Skill skill) {
		if (skill == null || isBlank(skill.getSkillId())) {
			throw new IllegalArgumentException("Skill id is required");
		}
		Skill previous = skills.putIfAbsent(skill.getSkillId(), skill);
		if (previous != null) {
			throw new IllegalArgumentException("Skill already registered: " + skill.getSkillId());
		}
		return skill;
	}

	public List<Skill> listSkills() {
		List<Skill> result = new ArrayList<>(skills.values());
		result.sort(Comparator.comparing(Skill::getSkillId));
		return result;
	}

	public Optional<Skill> getSkill(String skillId) {
		if (isBlank(skillId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(skills.get(skillId));
	}

	public Optional<Skill> enable(String skillId) {
		Optional<Skill> skill = getSkill(skillId);
		skill.ifPresent(Skill::enable);
		return skill;
	}

	public Optional<Skill> disable(String skillId) {
		Optional<Skill> skill = getSkill(skillId);
		skill.ifPresent(Skill::disable);
		return skill;
	}

	/**
	 * Resolves the enabled skills available to an agent. Explicit skillIds
	 * bindings take priority; otherwise skills are matched by capability.
	 */
	public List<Skill> getSkillsForAgent(String agentName) {
		if (isBlank(agentName)) {
			return List.of();
		}
		AgentDefinition agent = agentManager.getAgent(agentName);
		if (agent == null) {
			return List.of();
		}
		List<String> skillIds = agent.getSkillIds();
		if (skillIds != null && !skillIds.isEmpty()) {
			return skillIds.stream()
				.map(skills::get)
				.filter(Objects::nonNull)
				.filter(Skill::isEnabled)
				.toList();
		}
		List<String> capabilities = agent.getCapabilities();
		if (capabilities == null || capabilities.isEmpty()) {
			return List.of();
		}
		List<Skill> matched = new ArrayList<>();
		for (Skill skill : listSkills()) {
			if (skill.isEnabled() && capabilities.contains(capabilityFor(skill.getType()))) {
				matched.add(skill);
			}
		}
		return matched;
	}

	private String capabilityFor(SkillType type) {
		return switch (type == null ? SkillType.ANALYSIS : type) {
			case CODING -> "coding";
			case TESTING -> "testing";
			case BROWSER -> "browser";
			case ANALYSIS -> "analysis";
			case DEPLOYMENT -> "deployment";
		};
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
