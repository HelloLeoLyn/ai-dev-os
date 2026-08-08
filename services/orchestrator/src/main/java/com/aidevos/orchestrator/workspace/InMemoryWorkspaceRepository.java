package com.aidevos.orchestrator.workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory workspace store. Phase 1 is intentionally in-memory only (no
 * database migration is introduced), so this repository is registered for both
 * the in-memory and PostgreSQL persistence modes; a PostgreSQL-backed
 * implementation can be added in a later phase without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", matchIfMissing = true)
public class InMemoryWorkspaceRepository implements WorkspaceRepository {

	private final Map<String, Workspace> workspaces = new LinkedHashMap<>();

	@Override
	public synchronized void save(Workspace workspace) {
		workspaces.put(workspace.getWorkspaceId(), workspace);
	}

	@Override
	public synchronized Workspace get(String workspaceId) {
		return workspaces.get(workspaceId);
	}

	@Override
	public synchronized Optional<Workspace> getByProjectId(String projectId) {
		if (projectId == null) {
			return Optional.empty();
		}
		return workspaces.values().stream()
			.filter(workspace -> projectId.equals(workspace.getProjectId()))
			.findFirst();
	}

	@Override
	public synchronized List<Workspace> list() {
		return new ArrayList<>(workspaces.values());
	}

	@Override
	public synchronized boolean delete(String workspaceId) {
		return workspaces.remove(workspaceId) != null;
	}
}
