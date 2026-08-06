package com.aidevos.orchestrator.skill;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryServiceTest {

	private SkillRegistryService service;
	private AgentManager agentManager;

	@BeforeEach
	void setUp() {
		agentManager = new AgentManager();
		service = new SkillRegistryService(new SkillConfigLoader(), agentManager);
	}

	@Test
	void shouldLoadConfiguredSkills() {
		List<Skill> skills = service.listSkills();

		assertEquals(List.of("browser-skill", "coding-skill", "deployment-skill",
			"testing-skill"), skills.stream().map(Skill::getSkillId).toList());
		assertEquals(SkillType.CODING, service.getSkill("coding-skill").orElseThrow().getType());
		assertEquals(SkillType.BROWSER, service.getSkill("browser-skill").orElseThrow().getType());
		assertEquals(SkillType.DEPLOYMENT,
			service.getSkill("deployment-skill").orElseThrow().getType());
		assertEquals(SkillType.TESTING, service.getSkill("testing-skill").orElseThrow().getType());
	}

	@Test
	void shouldGetSkillById() {
		Skill coding = service.getSkill("coding-skill").orElseThrow();

		assertEquals("Coding Skill", coding.getName());
		assertEquals("1.0.0", coding.getVersion());
		assertTrue(coding.isEnabled());
		assertEquals(List.of("read_code", "edit_file", "run_build", "review_diff"),
			coding.getTools());
		assertTrue(coding.getInstructions().contains("minimal"));
	}

	@Test
	void shouldReturnEmptyForUnknownSkill() {
		assertTrue(service.getSkill("missing").isEmpty());
	}

	@Test
	void shouldEnableAndDisableSkill() {
		assertFalse(service.disable("coding-skill").orElseThrow().isEnabled());
		assertFalse(service.getSkill("coding-skill").orElseThrow().isEnabled());

		assertTrue(service.enable("coding-skill").orElseThrow().isEnabled());
		assertTrue(service.getSkill("coding-skill").orElseThrow().isEnabled());
	}

	@Test
	void shouldReturnEmptyWhenTogglingUnknownSkill() {
		assertTrue(service.enable("missing").isEmpty());
		assertTrue(service.disable("missing").isEmpty());
	}

	@Test
	void shouldResolveSkillsBoundToAgent() {
		agentManager.register(agent("coder", List.of("coding", "git"),
			List.of("coding-skill", "testing-skill")));

		List<Skill> skills = service.getSkillsForAgent("coder");

		assertEquals(List.of("coding-skill", "testing-skill"),
			skills.stream().map(Skill::getSkillId).toList());
	}

	@Test
	void shouldResolveSkillsByCapabilityWhenNoBindingConfigured() {
		agentManager.register(agent("tester", List.of("testing", "browser"), null));

		List<Skill> skills = service.getSkillsForAgent("tester");

		assertEquals(List.of("browser-skill", "testing-skill"),
			skills.stream().map(Skill::getSkillId).toList());
	}

	@Test
	void shouldExcludeDisabledSkillsFromAgentResolution() {
		agentManager.register(agent("coder", List.of("coding"), List.of("coding-skill")));
		service.disable("coding-skill");

		assertTrue(service.getSkillsForAgent("coder").isEmpty());
	}

	@Test
	void shouldReturnEmptyForUnknownAgent() {
		assertTrue(service.getSkillsForAgent("missing").isEmpty());
		assertTrue(service.getSkillsForAgent(null).isEmpty());
	}

	@Test
	void shouldRegisterSkillProgrammatically() {
		Skill skill = new Skill("analysis-skill", "Analysis Skill", "分析技能",
			SkillType.ANALYSIS, "1.0.0", true, List.of("analyze"), "Analyze first.");

		service.register(skill);

		assertEquals(Optional.of(skill), service.getSkill("analysis-skill"));
	}

	@Test
	void shouldRejectDuplicateRegistration() {
		Skill skill = new Skill("coding-skill", "Dup", "dup", SkillType.CODING, "1.0.0",
			true, List.of(), null);

		assertThrows(IllegalArgumentException.class, () -> service.register(skill));
	}

	@Test
	void shouldRejectRegistrationWithoutId() {
		Skill skill = new Skill(null, "NoId", "missing id", SkillType.CODING, "1.0.0",
			true, List.of(), null);

		assertThrows(IllegalArgumentException.class, () -> service.register(skill));
	}

	private AgentDefinition agent(String name, List<String> capabilities, List<String> skillIds) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name);
		agent.setExecutor("mock");
		agent.setCapabilities(capabilities);
		agent.setSkillIds(skillIds);
		return agent;
	}
}
