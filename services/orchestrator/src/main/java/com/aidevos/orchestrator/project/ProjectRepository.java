package com.aidevos.orchestrator.project;

import java.util.List;

/**
 * Persistence contract for projects. Implemented by the in-memory store and by
 * the PostgreSQL-backed repository.
 */
public interface ProjectRepository {

	void save(Project project);

	Project get(String projectId);

	List<Project> list();

	boolean delete(String projectId);
}
