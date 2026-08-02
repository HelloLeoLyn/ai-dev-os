package com.aidevos.orchestrator.executor.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitInspectorTest {

	@Test
	void shouldParseNullSeparatedUntrackedPaths() {
		GitExecutor executor = mock(GitExecutor.class);
		when(executor.branch("/workspace")).thenReturn(success("main\n"));
		when(executor.head("/workspace")).thenReturn(success("abc123\n"));
		when(executor.status("/workspace")).thenReturn(success("?? first.txt\n"));
		when(executor.diff("/workspace")).thenReturn(success(""));
		when(executor.patch("/workspace")).thenReturn(success(""));
		when(executor.cachedDiff("/workspace")).thenReturn(success(""));
		when(executor.untrackedFiles("/workspace"))
			.thenReturn(success("first.txt\u0000folder/second.txt\u0000"));

		GitSnapshot snapshot = new GitInspector(executor).capture("/workspace");

		assertEquals(java.util.List.of("first.txt", "folder/second.txt"), snapshot.untrackedFiles());
	}

	private GitResult success(String output) {
		GitResult result = new GitResult();
		result.setSuccess(true);
		result.setOutput(output);
		return result;
	}
}
