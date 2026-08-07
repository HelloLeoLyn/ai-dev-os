package com.aidevos.orchestrator.skill;

import java.util.List;

/**
 * Persistence boundary for the skill registry state. The registry writes
 * through on every change and restores the persisted state at startup.
 */
public interface SkillRepository {

	void save(Skill skill);

	Skill get(String skillId);

	List<Skill> list();

	boolean delete(String skillId);
}
