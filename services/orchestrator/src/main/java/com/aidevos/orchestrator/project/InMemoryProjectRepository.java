package com.aidevos.orchestrator.project;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryProjectRepository implements ProjectRepository {

	private final Map<String, Project> projects = new LinkedHashMap<>();

	@Override
	public synchronized void save(Project project) {
		projects.put(project.getProjectId(), project);
	}

	@Override
	public synchronized Project get(String projectId) {
		return projects.get(projectId);
	}

	@Override
	public synchronized List<Project> list() {
		return new ArrayList<>(projects.values());
	}

	@Override
	public synchronized boolean delete(String projectId) {
		return projects.remove(projectId) != null;
	}
}
