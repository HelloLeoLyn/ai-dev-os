package com.aidevos.orchestrator.skill;

import java.time.Instant;
import java.util.List;

/**
 * A reusable agent skill: a named, versioned bundle of tools and instructions
 * that an agent can load and reuse. Skills are managed by SkillRegistryService
 * and are independent from the execution engine, scheduler and worker.
 */
public class Skill {

	private final String skillId;
	private final String name;
	private final String description;
	private final SkillType type;
	private final String version;
	private final List<String> tools;
	private final String instructions;
	private final Instant createdAt;
	private volatile Instant updatedAt;
	private volatile boolean enabled;

	public Skill(String skillId, String name, String description, SkillType type,
			String version, boolean enabled, List<String> tools, String instructions) {
		this(skillId, name, description, type, version, enabled, tools, instructions,
			Instant.now(), Instant.now());
	}

	public Skill(String skillId, String name, String description, SkillType type,
			String version, boolean enabled, List<String> tools, String instructions,
			Instant createdAt, Instant updatedAt) {
		this.skillId = skillId;
		this.name = name;
		this.description = description;
		this.type = type == null ? SkillType.ANALYSIS : type;
		this.version = version;
		this.enabled = enabled;
		this.tools = tools == null ? List.of() : List.copyOf(tools);
		this.instructions = instructions;
		this.createdAt = createdAt == null ? Instant.now() : createdAt;
		this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
	}

	public synchronized void enable() {
		this.enabled = true;
		this.updatedAt = Instant.now();
	}

	public synchronized void disable() {
		this.enabled = false;
		this.updatedAt = Instant.now();
	}

	public String getSkillId() {
		return skillId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public SkillType getType() {
		return type;
	}

	public String getVersion() {
		return version;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public List<String> getTools() {
		return tools;
	}

	public String getInstructions() {
		return instructions;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
