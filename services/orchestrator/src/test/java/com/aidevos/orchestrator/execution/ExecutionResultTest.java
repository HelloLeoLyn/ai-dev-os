package com.aidevos.orchestrator.execution;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionResultTest {

	@Test
	void shouldPreserveExistingResultProperties() {
		ExecutionResult result = new ExecutionResult();

		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		result.setOutput("completed");

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("completed", result.getOutput());
	}

	@Test
	void shouldInitializeArtifacts() {
		ExecutionResult result = new ExecutionResult();

		assertNotNull(result.getArtifacts());
		assertTrue(result.getArtifacts().isEmpty());
	}

	@Test
	void shouldStoreArtifactProperties() {
		ExecutionArtifact artifact = new ExecutionArtifact();
		artifact.setType("SCREENSHOT");
		artifact.setName("homepage");
		artifact.setMediaType("image/png");
		artifact.setUri("file:///workspace/homepage.png");
		artifact.setContent(null);
		artifact.setMetadata(Map.of("width", 1280, "height", 720));

		ExecutionResult result = new ExecutionResult();
		result.setArtifacts(List.of(artifact));

		ExecutionArtifact stored = result.getArtifacts().getFirst();
		assertEquals("SCREENSHOT", stored.getType());
		assertEquals("homepage", stored.getName());
		assertEquals("image/png", stored.getMediaType());
		assertEquals("file:///workspace/homepage.png", stored.getUri());
		assertEquals(Map.of("width", 1280, "height", 720), stored.getMetadata());
	}
}
