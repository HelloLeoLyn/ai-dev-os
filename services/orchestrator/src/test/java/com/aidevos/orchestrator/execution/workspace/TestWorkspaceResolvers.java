package com.aidevos.orchestrator.execution.workspace;

import java.nio.file.Path;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.git.GitExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Legacy integration-test wiring for suites whose concern is not workspace ownership. */
public final class TestWorkspaceResolvers {

	private TestWorkspaceResolvers() { }

	public static WorkspaceResolver create(CodingWorkspaceProperties properties,
			GitExecutor gitExecutor) {
		TaskWorkspaceTrustService trust = mock(TaskWorkspaceTrustService.class);
		when(trust.requireTrustedWorkspace(any(ExecutionContext.class), any(Path.class)))
			.thenAnswer(invocation -> invocation.getArgument(1));
		return new WorkspaceResolver(properties, gitExecutor, trust);
	}
}
