package com.aidevos.orchestrator.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkspaceContextTest {

	@Test
	void shouldCreateWorkspaceContext() {
		WorkspaceContext context = new WorkspaceContext();

		assertNotNull(context);
	}

	@Test
	void shouldStoreAndReturnProperties() {
		WorkspaceContext context = new WorkspaceContext();

		context.setPath("/workspace/project");
		context.setProjectName("orchestrator");
		context.setGitBranch("main");

		assertEquals("/workspace/project", context.getPath());
		assertEquals("orchestrator", context.getProjectName());
		assertEquals("main", context.getGitBranch());
	}
}
