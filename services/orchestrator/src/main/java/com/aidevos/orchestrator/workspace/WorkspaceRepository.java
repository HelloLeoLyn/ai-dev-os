package com.aidevos.orchestrator.workspace;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for workspaces. Implemented by the in-memory store; no
 * database migration is introduced.
 */
public interface WorkspaceRepository {

	void save(Workspace workspace);

	Workspace get(String workspaceId);

	Optional<Workspace> getByProjectId(String projectId);

	List<Workspace> list();

	boolean delete(String workspaceId);
}
