package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.skill.Skill;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill management API: list skills, view skill detail, toggle the enabled
 * state and query the skills available to an agent.
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

	private final SkillRegistryService registry;

	public SkillController(SkillRegistryService registry) {
		this.registry = registry;
	}

	@GetMapping
	public List<Skill> list() {
		return registry.listSkills();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Skill> get(@PathVariable String id) {
		return registry.getSkill(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/agents/{agentName}")
	public List<Skill> getSkillsForAgent(@PathVariable String agentName) {
		return registry.getSkillsForAgent(agentName);
	}

	@PostMapping("/{id}/enable")
	public ResponseEntity<Skill> enable(@PathVariable String id) {
		return registry.enable(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/disable")
	public ResponseEntity<Skill> disable(@PathVariable String id) {
		return registry.disable(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}
}
