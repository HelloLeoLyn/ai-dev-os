package com.aidevos.orchestrator.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactContentLimiterTest {

	@Test
	void shouldTruncateAndRecordLengths() {
		ExecutionArtifact artifact = new ExecutionArtifact();

		new ArtifactContentLimiter(4).apply(artifact, "123456");

		assertEquals("1234", artifact.getContent());
		assertEquals(true, artifact.getMetadata().get("truncated"));
		assertEquals(6, artifact.getMetadata().get("originalLength"));
		assertEquals(4, artifact.getMetadata().get("storedLength"));
	}
}
