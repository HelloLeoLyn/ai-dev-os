package com.aidevos.orchestrator.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySkillRepository implements SkillRepository {

	private final Map<String, Skill> skills = new LinkedHashMap<>();

	@Override
	public synchronized void save(Skill skill) {
		skills.put(skill.getSkillId(), skill);
	}

	@Override
	public synchronized Skill get(String skillId) {
		return skills.get(skillId);
	}

	@Override
	public synchronized List<Skill> list() {
		return new ArrayList<>(skills.values());
	}

	@Override
	public synchronized boolean delete(String skillId) {
		return skills.remove(skillId) != null;
	}
}
