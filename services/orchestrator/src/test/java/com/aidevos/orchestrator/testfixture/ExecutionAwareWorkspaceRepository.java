package com.aidevos.orchestrator.testfixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.Workspace;

/**
 * Test-only adapter for legacy change/commit services: once an isolated
 * worktree exists, the registered workspace id resolves to that worktree.
 * Production workspace resolution is not changed.
 */
public final class ExecutionAwareWorkspaceRepository extends InMemoryWorkspaceRepository {
    private final Path executionRoot;

    public ExecutionAwareWorkspaceRepository(Path executionRoot) {
        this.executionRoot = executionRoot.toAbsolutePath().normalize();
    }

    @Override
    public synchronized Workspace get(String workspaceId) {
        Workspace source = super.get(workspaceId);
        if (source == null || !Files.isDirectory(executionRoot)) {
            return source;
        }
        try (var paths = Files.list(executionRoot)) {
            Path worktree = paths.filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve(".git")) || Files.exists(path.resolve(".git")))
                    .max(Comparator.comparingLong(this::modifiedAt))
                    .orElse(null);
            if (worktree == null) {
                return source;
            }
            return new Workspace(source.getWorkspaceId(), source.getProjectId(), worktree.toString(),
                    source.getBranch(), source.getStatus(), source.getCreatedAt(), source.getUpdatedAt(),
                    source.getRepositoryUrl());
        }
        catch (Exception ignored) {
            return source;
        }
    }

    private long modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (Exception ignored) {
            return Long.MIN_VALUE;
        }
    }
}
