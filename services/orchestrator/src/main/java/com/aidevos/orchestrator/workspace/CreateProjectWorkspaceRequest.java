package com.aidevos.orchestrator.workspace;

/**
 * Project-scoped request that registers an existing local Git directory. When
 * path is omitted, the owning project's configured path is used.
 */
public record CreateProjectWorkspaceRequest(String path) {
}
