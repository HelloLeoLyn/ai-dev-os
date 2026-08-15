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
}
