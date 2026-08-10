package com.aidevos.orchestrator.project;

/**
 * Request body for creating a project.
 */
public record CreateProjectRequest(String name, String path, String description,
		String repositoryUrl, String defaultBranch) {

	public CreateProjectRequest(String name, String path, String description) {
		this(name, path, description, null, null);
	}
}
