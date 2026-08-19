package com.aidevos.orchestrator.executor.codex;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodexResultMapperTest {

	@Test
	void shouldMapThreadAndLastAgentMessage() {
		String events = """
			{"type":"thread.started","thread_id":"thread-1"}
			{"type":"item.completed","item":{"type":"agent_message","text":"Implemented feature"}}
			""";

		CodexOutput output = new CodexResultMapper(new ObjectMapper()).map(events);

		assertEquals("thread-1", output.threadId());
		assertEquals("Implemented feature", output.summary());
	}

	@Test
	void shouldIgnoreNonJsonOutput() {
		CodexOutput output = new CodexResultMapper(new ObjectMapper()).map("plain output");

		assertEquals(null, output.threadId());
		assertEquals(null, output.summary());
	}

	@Test
	void shouldReadSummaryFromStructuredAgentMessage() {
		String events = """
			{"type":"item.completed","item":{"type":"agent_message","text":"{\\"summary\\":\\"Tests passed\\",\\"changedFiles\\":[],\\"tests\\":[],\\"risks\\":[]}"}}
			""";

		CodexOutput output = new CodexResultMapper(new ObjectMapper()).map(events);
		assertEquals("Tests passed", output.summary());
		assertEquals("{\"summary\":\"Tests passed\",\"changedFiles\":[],\"tests\":[],\"risks\":[]}",
			output.structuredPayload());
	}

	@Test
	void shouldExtractStructuredTurnFailureOverStderr() {
		String events = """
			{"type":"thread.started","thread_id":"thread-9"}
			{"type":"turn.failed","failure":{"error":{"type":"usage_limit","message":"You've hit your usage limit. Please try again later."}}}
			""";

		CodexOutput output = new CodexResultMapper(new ObjectMapper()).map(events);

		assertEquals("thread-9", output.threadId());
		assertEquals("usage_limit", output.failureType());
		assertEquals("You've hit your usage limit. Please try again later.", output.failureMessage());
	}

	@Test
	void shouldFallBackToFailureReasonAndEventError() {
		String reason = """
			{"type":"turn.failed","failure":{"reason":"Provider disabled"}}
			""";
		CodexOutput fromReason = new CodexResultMapper(new ObjectMapper()).map(reason);
		assertEquals("Provider disabled", fromReason.failureMessage());

		String eventError = """
			{"type":"turn.failed","error":"Model not found"}
			""";
		CodexOutput fromEvent = new CodexResultMapper(new ObjectMapper()).map(eventError);
		assertEquals("Model not found", fromEvent.failureMessage());
	}
}
