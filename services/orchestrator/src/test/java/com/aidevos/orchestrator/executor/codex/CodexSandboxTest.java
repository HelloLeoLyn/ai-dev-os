package com.aidevos.orchestrator.executor.codex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CodexSandboxTest {

	@Test
	void shouldAcceptSupportedSandboxModes() {
		assertEquals(CodexSandbox.READ_ONLY, CodexSandbox.parse("read-only"));
		assertEquals(CodexSandbox.WORKSPACE_WRITE, CodexSandbox.parse("workspace-write"));
	}

	@Test
	void shouldDefaultToWorkspaceWrite() {
		assertEquals(CodexSandbox.WORKSPACE_WRITE, CodexSandbox.parse(null));
		assertEquals(CodexSandbox.WORKSPACE_WRITE, CodexSandbox.parse(" "));
	}

	@Test
	void shouldRejectDangerFullAccess() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> CodexSandbox.parse("danger-full-access"));

		assertEquals("Unsupported Codex sandbox: danger-full-access", exception.getMessage());
	}
}
