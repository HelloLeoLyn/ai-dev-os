package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.skill.Skill;
import com.aidevos.orchestrator.skill.SkillRegistryService;
import com.aidevos.orchestrator.skill.SkillType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SkillControllerTest {

	@Test
	void shouldListSkills() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		when(registry.listSkills()).thenReturn(List.of(skill()));
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/skills"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].skillId").value("coding-skill"))
			.andExpect(jsonPath("$[0].type").value("CODING"))
			.andExpect(jsonPath("$[0].version").value("1.0.0"))
			.andExpect(jsonPath("$[0].enabled").value(true))
			.andExpect(jsonPath("$[0].tools[0]").value("read_code"));
	}

	@Test
	void shouldGetSkillDetail() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		when(registry.getSkill("coding-skill")).thenReturn(Optional.of(skill()));
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/skills/coding-skill"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.skillId").value("coding-skill"))
			.andExpect(jsonPath("$.name").value("Coding Skill"))
			.andExpect(jsonPath("$.instructions").value("Make minimal changes."));
	}

	@Test
	void shouldReturn404WhenSkillMissing() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		when(registry.getSkill("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/skills/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldGetSkillsForAgent() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		when(registry.getSkillsForAgent("coder")).thenReturn(List.of(skill()));
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/skills/agents/coder"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].skillId").value("coding-skill"));
	}

	@Test
	void shouldEnableSkill() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		Skill skill = skill();
		skill.enable();
		when(registry.enable("coding-skill")).thenReturn(Optional.of(skill));
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/skills/coding-skill/enable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void shouldDisableSkill() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		Skill skill = skill();
		skill.disable();
		when(registry.disable("coding-skill")).thenReturn(Optional.of(skill));
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/skills/coding-skill/disable"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled").value(false));
	}

	@Test
	void shouldReturn404WhenTogglingMissingSkill() throws Exception {
		SkillRegistryService registry = mock(SkillRegistryService.class);
		when(registry.enable("missing")).thenReturn(Optional.empty());
		when(registry.disable("missing")).thenReturn(Optional.empty());
		MockMvc mockMvc = standaloneSetup(new SkillController(registry)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/skills/missing/enable"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/skills/missing/disable"))
			.andExpect(status().isNotFound());
	}

	private Skill skill() {
		return new Skill("coding-skill", "Coding Skill", "编码技能", SkillType.CODING,
			"1.0.0", true, List.of("read_code", "edit_file"), "Make minimal changes.");
	}
}
