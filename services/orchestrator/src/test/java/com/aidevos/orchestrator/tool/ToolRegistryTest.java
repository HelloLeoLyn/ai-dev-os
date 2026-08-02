package com.aidevos.orchestrator.tool;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTest {

	@Test
	void shouldRegisterProviderAndTools() {
		FakeToolProvider provider = new FakeToolProvider("fake", "echo",
			invocation -> ToolResult.success("ok", List.of()));

		ToolRegistry registry = new ToolRegistry(List.of(provider));

		assertSame(provider, registry.getProvider("fake"));
		assertEquals("echo", registry.getTool("fake", "echo").name());
		assertEquals(1, registry.getTools().size());
	}

	@Test
	void shouldRejectDuplicateProvider() {
		FakeToolProvider first = new FakeToolProvider("fake", "one",
			invocation -> ToolResult.success("ok", List.of()));
		FakeToolProvider second = new FakeToolProvider("fake", "two",
			invocation -> ToolResult.success("ok", List.of()));

		assertThrows(IllegalStateException.class, () -> new ToolRegistry(List.of(first, second)));
	}
}
