package com.aidevos.orchestrator.workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
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
	private final AuditService auditService;

	public WorkspaceService(WorkspaceRepository repository,
			GitCommandExecutor gitCommandExecutor) {
		this(repository, gitCommandExecutor, AuditService.noop());
	}

	@org.springframework.beans.factory.annotation.Autowired
	public WorkspaceService(WorkspaceRepository repository,
			GitCommandExecutor gitCommandExecutor, AuditService auditService) {
		this.repository = repository;
		this.gitCommandExecutor = gitCommandExecutor;
		this.auditService = auditService;
	}

	public Workspace createWorkspace(String projectId, String path) {
		return createProjectWorkspace(projectId, path, null);
	}

	/**
	 * Creates a workspace bound to an existing project. The projectId is
	 * mandatory; the repositoryUrl is optional metadata for the multi-project
	 * model.
	 */
	public Workspace createProjectWorkspace(String projectId, String path,
			String repositoryUrl) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required");
		}
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path is required");
		}
		String normalizedPath = path.trim();
		Path directory = Path.of(normalizedPath);
		if (!Files.isDirectory(directory)) {
			throw new IllegalArgumentException("Workspace path is not a directory: " + path);
		}
		GitStatus gitStatus = gitCommandExecutor.status(normalizedPath);
		if (gitStatus == null || gitStatus.getBranch() == null || gitStatus.getBranch().isBlank()) {
			throw new IllegalArgumentException(
				"Workspace path is not a Git repository with a current branch: " + path);
		}
		String workspaceId = "workspace-" + UUID.randomUUID();
		String normalizedProjectId = projectId.trim();
		Workspace workspace = new Workspace(workspaceId, normalizedProjectId, normalizedPath,
			gitStatus.getBranch(), WorkspaceStatus.READY, Instant.now(), Instant.now(), repositoryUrl);
		repository.save(workspace);
		auditService.projectEvent(EventType.PROJECT_WORKSPACE_CREATED, normalizedProjectId,
			"Workspace created for project: " + normalizedProjectId,
			Map.of("projectId", normalizedProjectId, "workspaceId", workspaceId));
		return workspace;
	}

	public List<Workspace> getProjectWorkspaces(String projectId) {
		if (projectId == null || projectId.isBlank()) {
			return List.of();
		}
		return repository.listByProjectId(projectId.trim());
	}

	/**
	 * Verifies that the workspace belongs to the given project. Returns false
	 * for unknown workspaces.
	 */
	public boolean checkProjectOwnership(String projectId, String workspaceId) {
		if (projectId == null || workspaceId == null) {
			return false;
		}
		return getWorkspace(workspaceId)
			.map(workspace -> projectId.equals(workspace.getProjectId()))
			.orElse(false);
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
