package com.aidevos.orchestrator.workspace;

/**
 * Request that registers an existing local directory as a workspace for a
 * project.
 */
public record CreateWorkspaceRequest(String projectId, String path) {
}
