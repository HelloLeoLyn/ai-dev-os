package com.aidevos.orchestrator.modelregistry;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helper that supplies a CodexExecutor model resolver resolving a fixed
 * default model. Integration tests that exercise orchestrator flows (workspace,
 * approvals, commit) rather than model routing use this so executions still run
 * through the fail-closed resolution path with a concrete model.
 */
public final class ModelTestSupport {

	private ModelTestSupport() {
	}

	public static ModelResolver defaultResolver() {
		ModelResolver resolver = mock(ModelResolver.class);
		when(resolver.resolve(nullable(String.class), nullable(String.class)))
			.thenReturn(new ResolvedModel("AUTO", "gpt-5.6-codex", "openai", "codex", null, null));
		return resolver;
	}
}
