package com.aidevos.orchestrator.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskDefinitionTest {

	@Test
	void shouldCreateTaskDefinition() {
		TaskDefinition taskDefinition = new TaskDefinition();

		assertNotNull(taskDefinition);
	}

	@Test
	void shouldStoreAndReturnProperties() {
		TaskDefinition taskDefinition = new TaskDefinition();

		taskDefinition.setId("task-1");
		taskDefinition.setName("Plan implementation");
		taskDefinition.setDescription("Create an implementation plan");
		taskDefinition.setAgentName("planner");
		taskDefinition.setRequiredCapabilities(List.of("analysis"));
		taskDefinition.setStatus("pending");

		assertEquals("task-1", taskDefinition.getId());
		assertEquals("Plan implementation", taskDefinition.getName());
		assertEquals("Create an implementation plan", taskDefinition.getDescription());
		assertEquals("planner", taskDefinition.getAgentName());
		assertEquals(List.of("analysis"), taskDefinition.getRequiredCapabilities());
		assertEquals("pending", taskDefinition.getStatus());
	}
}
