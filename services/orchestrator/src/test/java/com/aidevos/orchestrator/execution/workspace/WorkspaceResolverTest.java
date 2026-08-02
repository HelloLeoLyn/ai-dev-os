package com.aidevos.orchestrator.execution.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceResolverTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldResolveAllowedGitWorkspace() throws Exception {
		Path repository = Files.createDirectory(tempDir.resolve("project"));
		Files.createDirectory(repository.resolve(".git"));
		CodingWorkspaceProperties properties = properties(tempDir);
		ExecutionContext context = context(repository);

		WorkspaceSnapshot snapshot = resolver(properties, true).resolve(context);

		assertEquals(repository.toRealPath().toString(), snapshot.path());
		assertEquals("project", snapshot.projectName());
	}

	@Test
	void shouldRejectWorkspaceOutsideAllowedRoot() throws Exception {
		Path allowed = Files.createDirectory(tempDir.resolve("allowed"));
		Path outside = Files.createDirectory(tempDir.resolve("outside"));
		Files.createDirectory(outside.resolve(".git"));

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> resolver(properties(allowed), true).resolve(context(outside)));

		assertEquals("Coding workspace is outside allowed roots: " + outside.toRealPath(),
			exception.getMessage());
	}

	@Test
	void shouldRejectNonGitWorkspace() throws Exception {
		Path workspace = Files.createDirectory(tempDir.resolve("plain"));

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
			() -> resolver(properties(tempDir), false).resolve(context(workspace)));

		assertEquals("Coding workspace is not inside a Git repository: " + workspace.toRealPath(),
			exception.getMessage());
	}

	private CodingWorkspaceProperties properties(Path allowedRoot) {
		CodingWorkspaceProperties properties = new CodingWorkspaceProperties();
		properties.setAllowedRoots(List.of(allowedRoot.toString()));
		return properties;
	}

	private WorkspaceResolver resolver(CodingWorkspaceProperties properties, boolean repository) {
		GitExecutor gitExecutor = mock(GitExecutor.class);
		GitResult result = new GitResult();
		result.setSuccess(repository);
		result.setOutput(repository ? "true\n" : "");
		when(gitExecutor.isRepository(org.mockito.ArgumentMatchers.anyString())).thenReturn(result);
		return new WorkspaceResolver(properties, gitExecutor);
	}

	private ExecutionContext context(Path workspace) {
		ExecutionContext context = new ExecutionContext();
		context.setParameters(Map.of("coding", Map.of("workspace", workspace.toString())));
		return context;
	}
}
