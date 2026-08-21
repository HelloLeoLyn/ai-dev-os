package com.aidevos.orchestrator.ci;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mock provider is deterministically completable: trigger -> RUNNING, the
 * next poll observes SUCCESS, later polls stay SUCCESS, a re-trigger restarts
 * at RUNNING, and the test-only setStatus override (FAILED) still wins.
 */
class MockCiProviderTest {

	@Test
	void completesOnNextPollAndStaysTerminal() {
		MockCiProvider provider = new MockCiProvider();
		CiTriggerRequest request = new CiTriggerRequest("pr-1", "main", "abc123");

		CiTriggerResult triggered = provider.trigger(request);
		assertEquals(CiStatus.RUNNING, provider.getStatus(triggered.pipelineId()).status());
		assertEquals(CiStatus.SUCCESS, provider.getStatus(triggered.pipelineId()).status());
		assertEquals(CiStatus.SUCCESS, provider.getStatus(triggered.pipelineId()).status());

		provider.setStatus(triggered.pipelineId(), CiStatus.FAILED);
		assertEquals(CiStatus.FAILED, provider.getStatus(triggered.pipelineId()).status());

		CiTriggerResult retriggered = provider.trigger(request);
		assertEquals(CiStatus.RUNNING, provider.getStatus(retriggered.pipelineId()).status());
		assertEquals(CiStatus.SUCCESS, provider.getStatus(retriggered.pipelineId()).status());
	}
}
