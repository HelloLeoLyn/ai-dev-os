package com.aidevos.orchestrator.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

	@TempDir
	Path tempDir;

	private GitCommandExecutor gitCommandExecutor;
	private WorkspaceService service;

	@BeforeEach
	void setUp() {
		gitCommandExecutor = mock(GitCommandExecutor.class);
		service = new WorkspaceService(new InMemoryWorkspaceRepository(), gitCommandExecutor);
	}

	@Test
	void shouldCreateWorkspaceFromExistingDirectory() {
		Workspace workspace = service.createWorkspace("project-a", tempDir.toString());

		assertNotNull(workspace.getWorkspaceId());
		assertEquals("project-a", workspace.getProjectId());
		assertEquals(tempDir.toString(), workspace.getPath());
		assertEquals(WorkspaceStatus.READY, workspace.getStatus());
		assertNotNull(workspace.getCreatedAt());
		assertNotNull(workspace.getUpdatedAt());
	}

	@Test
	void shouldRejectMissingDirectory() {
		String missing = tempDir.resolve("does-not-exist").toString();
		assertThrows(IllegalArgumentException.class,
			() -> service.createWorkspace("project-a", missing));
	}

	@Test
	void shouldRejectBlankProjectId() {
		assertThrows(IllegalArgumentException.class,
			() -> service.createWorkspace("  ", tempDir.toString()));
		assertThrows(IllegalArgumentException.class,
			() -> service.createWorkspace("project-a", " "));
	}

	@Test
	void shouldGetWorkspaceByProject() {
		Workspace workspace = service.createWorkspace("project-a", tempDir.toString());

		assertTrue(service.getWorkspace(workspace.getWorkspaceId()).isPresent());
		assertEquals(workspace.getWorkspaceId(),
			service.getProjectWorkspace("project-a").orElseThrow().getWorkspaceId());
		assertFalse(service.getProjectWorkspace("unknown").isPresent());
		assertFalse(service.getWorkspace("missing").isPresent());
	}

	@Test
	void shouldLockAndReleaseWorkspace() {
		Workspace workspace = service.createWorkspace("project-a", tempDir.toString());

		Workspace locked = service.lockWorkspace(workspace.getWorkspaceId());
		assertEquals(WorkspaceStatus.LOCKED, locked.getStatus());
		assertEquals(WorkspaceStatus.LOCKED, service.getWorkspace(workspace.getWorkspaceId())
			.orElseThrow().getStatus());

		Workspace released = service.releaseWorkspace(workspace.getWorkspaceId());
		assertEquals(WorkspaceStatus.READY, released.getStatus());
	}

	@Test
	void shouldReturn404ForMissingWorkspace() {
		assertThrows(ResourceNotFoundException.class, () -> service.lockWorkspace("missing"));
		assertThrows(ResourceNotFoundException.class, () -> service.releaseWorkspace("missing"));
		assertThrows(ResourceNotFoundException.class, () -> service.checkGitStatus("missing"));
		assertThrows(ResourceNotFoundException.class, () -> service.getGitDiff("missing"));
	}

	@Test
	void shouldDelegateGitInspectionToExecutor() {
		Workspace workspace = service.createWorkspace("project-a", tempDir.toString());
		GitStatus status = new GitStatus("main", 3, 2, 0);
		GitDiff diff = new GitDiff(1, 2, 1, "1 file changed, 2 insertions(+), 1 deletion(-)");
		when(gitCommandExecutor.status(tempDir.toString())).thenReturn(status);
		when(gitCommandExecutor.diff(tempDir.toString())).thenReturn(diff);

		assertEquals(status, service.checkGitStatus(workspace.getWorkspaceId()));
		assertEquals(diff, service.getGitDiff(workspace.getWorkspaceId()));
		verify(gitCommandExecutor).status(tempDir.toString());
		verify(gitCommandExecutor).diff(tempDir.toString());
	}

	@Test
	void shouldListWorkspacesNewestFirst() {
		Path first = tempDir.resolve("first");
		Path second = tempDir.resolve("second");
		assertTrue(first.toFile().mkdirs());
		assertTrue(second.toFile().mkdirs());
		Workspace a = service.createWorkspace("project-a", first.toString());
		Workspace b = service.createWorkspace("project-b", second.toString());

		assertEquals(2, service.listWorkspaces().size());
		assertEquals(b.getWorkspaceId(), service.listWorkspaces().getFirst().getWorkspaceId());
		assertTrue(service.listWorkspaces().stream()
			.anyMatch(workspace -> workspace.getWorkspaceId().equals(a.getWorkspaceId())));
	}
}
