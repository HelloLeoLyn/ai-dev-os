package com.aidevos.orchestrator.project;

/**
 * Request body for creating a project.
 */
public record CreateProjectRequest(String name, String path, String description) {
}
