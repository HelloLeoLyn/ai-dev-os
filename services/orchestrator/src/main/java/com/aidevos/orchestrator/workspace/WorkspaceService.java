package com.aidevos.orchestrator.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.springframework.stereotype.Service;

/**
 * Manages git workspaces for agent code operations. Phase 1 only manages
 * existing local directories: register, query, lock/release and read-only git
 * inspection. Git clone, pull, checkout and automatic branch creation are
 * intentionally not implemented here.
 */
@Service
public class WorkspaceService {

	private final WorkspaceRepository repository;
	private final GitCommandExecutor gitCommandExecutor;

	public WorkspaceService(WorkspaceRepository repository,
			GitCommandExecutor gitCommandExecutor) {
		this.repository = repository;
		this.gitCommandExecutor = gitCommandExecutor;
	}

	public Workspace createWorkspace(String projectId, String path) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required");
		}
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path is required");
		}
		Path directory = Path.of(path);
		if (!Files.isDirectory(directory)) {
			throw new IllegalArgumentException("Workspace path is not a directory: " + path);
		}
		String workspaceId = "workspace-" + UUID.randomUUID();
		Workspace workspace = new Workspace(workspaceId, projectId.trim(), path.trim(),
			null, WorkspaceStatus.READY, Instant.now(), Instant.now());
		repository.save(workspace);
		return workspace;
	}

	public Optional<Workspace> getWorkspace(String workspaceId) {
		if (workspaceId == null || workspaceId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(workspaceId));
	}

	public Optional<Workspace> getProjectWorkspace(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			return Optional.empty();
		}
		return repository.getByProjectId(projectId);
	}

	public List<Workspace> listWorkspaces() {
		List<Workspace> result = new ArrayList<>(repository.list());
		result.sort(Comparator.comparing(Workspace::getCreatedAt).reversed());
		return result;
	}

	public Workspace lockWorkspace(String workspaceId) {
		Workspace workspace = requireWorkspace(workspaceId);
		workspace.lock();
		repository.save(workspace);
		return workspace;
	}

	public Workspace releaseWorkspace(String workspaceId) {
		Workspace workspace = requireWorkspace(workspaceId);
		workspace.markReady();
		repository.save(workspace);
		return workspace;
	}

	public GitStatus checkGitStatus(String workspaceId) {
		Workspace workspace = requireWorkspace(workspaceId);
		return gitCommandExecutor.status(workspace.getPath());
	}

	public GitDiff getGitDiff(String workspaceId) {
		Workspace workspace = requireWorkspace(workspaceId);
		return gitCommandExecutor.diff(workspace.getPath());
	}

	/**
	 * Returns the full working-tree diff (patch) of the workspace, or an empty
	 * string when the working tree is clean. Read-only.
	 */
	public String getGitDiffContent(String workspaceId) {
		Workspace workspace = requireWorkspace(workspaceId);
		return gitCommandExecutor.patch(workspace.getPath());
	}

	private Workspace requireWorkspace(String workspaceId) {
		return getWorkspace(workspaceId)
			.orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
	}
}
