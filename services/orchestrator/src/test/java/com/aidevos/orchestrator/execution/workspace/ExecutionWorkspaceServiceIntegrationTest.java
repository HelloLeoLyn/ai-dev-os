package com.aidevos.orchestrator.execution.workspace;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.execution.ExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

class ExecutionWorkspaceServiceIntegrationTest {
    @TempDir Path temp;

    @Test
    void createsAndReusesWorktreeWithoutSourcePollution() throws Exception {
        Path source = initRepo();
        Path root = temp.resolve("worktrees");
        WorkspaceService sources = mock(WorkspaceService.class);
        when(sources.getWorkspace("workspace-1")).thenReturn(java.util.Optional.of(
            new Workspace("workspace-1", "project-1", source.toString(), "master", WorkspaceStatus.READY, Instant.now(), Instant.now())));
        CodingWorkspaceProperties properties = new CodingWorkspaceProperties(); properties.setExecutionWorkspaceRoot(root.toString());
        InMemoryExecutionWorkspaceRepository repository = new InMemoryExecutionWorkspaceRepository();
        ExecutionWorkspaceService service = new ExecutionWorkspaceService(repository, sources, new CommandExecutor(), properties);
        ExecutionContext context = context("task-1", "job-1", source);
        String beforeStatus = git(source, "status", "--porcelain");

        ExecutionWorkspace first = service.ensureReady(context);
        Path execution = Path.of(first.getExecutionWorkspace());
        Files.writeString(execution.resolve("tracked.txt"), "execution change");
        Files.writeString(execution.resolve("untracked.txt"), "new");
        assertTrue(git(execution, "diff").contains("execution change"));
        assertEquals(beforeStatus, git(source, "status", "--porcelain"));
        assertEquals("baseline", Files.readString(source.resolve("tracked.txt")));

        ExecutionWorkspace second = service.ensureReady(context("task-1", "job-2", source));
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getExecutionWorkspace(), second.getExecutionWorkspace());
        assertNotEquals(first.getExecutionWorkspace(), source.toString());
    }

    @Test
    void dirtySourceRemainsDirtyAndWorktreeUsesCommittedHead() throws Exception {
        Path source = initRepo();
        Files.writeString(source.resolve("tracked.txt"), "source dirty");
        String before = git(source, "status", "--porcelain");
        Path root = temp.resolve("worktrees");
        WorkspaceService sources = mock(WorkspaceService.class);
        when(sources.getWorkspace("workspace-1")).thenReturn(java.util.Optional.of(
            new Workspace("workspace-1", "project-1", source.toString(), "master", WorkspaceStatus.READY, Instant.now(), Instant.now())));
        CodingWorkspaceProperties properties = new CodingWorkspaceProperties(); properties.setExecutionWorkspaceRoot(root.toString());
        ExecutionWorkspaceService service = new ExecutionWorkspaceService(new InMemoryExecutionWorkspaceRepository(), sources, new CommandExecutor(), properties);
        ExecutionWorkspace value = service.ensureReady(context("task-dirty", "job-1", source));
        assertEquals("baseline", Files.readString(Path.of(value.getExecutionWorkspace()).resolve("tracked.txt")));
        assertEquals(before, git(source, "status", "--porcelain"));
    }

    @Test
    void worktreeFailureNeverFallsBackToSourceWorkspace() throws Exception {
        Path source = initRepo();
        Path root = temp.resolve("worktrees");
        Files.createDirectories(root.resolve("task-collision"));
        WorkspaceService sources = mock(WorkspaceService.class);
        when(sources.getWorkspace("workspace-1")).thenReturn(java.util.Optional.of(
            new Workspace("workspace-1", "project-1", source.toString(), "master", WorkspaceStatus.READY, Instant.now(), Instant.now())));
        CodingWorkspaceProperties properties = new CodingWorkspaceProperties(); properties.setExecutionWorkspaceRoot(root.toString());
        InMemoryExecutionWorkspaceRepository repository = new InMemoryExecutionWorkspaceRepository();
        ExecutionWorkspaceService service = new ExecutionWorkspaceService(repository, sources, new CommandExecutor(), properties);
        assertThrows(IllegalStateException.class, () -> service.ensureReady(context("task-collision", "job-1", source)));
        assertNull(repository.findByTaskId("task-collision"));
        assertEquals("baseline", Files.readString(source.resolve("tracked.txt")));
    }

    @Test
    void nonGitSourceFailsWithoutSourceFallback() throws Exception {
        Path source = temp.resolve("not-a-repo"); Files.createDirectories(source);
        Path root = temp.resolve("worktrees");
        WorkspaceService sources = mock(WorkspaceService.class);
        when(sources.getWorkspace("workspace-1")).thenReturn(java.util.Optional.of(
            new Workspace("workspace-1", "project-1", source.toString(), "master", WorkspaceStatus.READY, Instant.now(), Instant.now())));
        CodingWorkspaceProperties properties = new CodingWorkspaceProperties(); properties.setExecutionWorkspaceRoot(root.toString());
        ExecutionWorkspaceService service = new ExecutionWorkspaceService(new InMemoryExecutionWorkspaceRepository(), sources, new CommandExecutor(), properties);
        assertThrows(IllegalStateException.class, () -> service.ensureReady(context("task-non-git", "job-1", source)));
        assertFalse(Files.exists(root.resolve("task-non-git")));
    }

    @Test
    void executionWorkspaceJsonRoundTripPreservesIdentityAndStatus() throws Exception {
        ExecutionWorkspace original = new ExecutionWorkspace("workspace-id", "task-1", "project-1", "workspace-1",
            "/source", "/execution", "GIT_WORKTREE", ExecutionWorkspaceStatus.READY, "abc123",
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"));
        ExecutionWorkspace restored = new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(original), ExecutionWorkspace.class);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getTaskId(), restored.getTaskId());
        assertEquals(original.getExecutionWorkspace(), restored.getExecutionWorkspace());
        assertEquals(original.getBaseRevision(), restored.getBaseRevision());
        assertEquals(original.getStatus(), restored.getStatus());
    }

    private ExecutionContext context(String task, String job, Path source) {
        ExecutionContext context = new ExecutionContext(); context.setTaskId(task); context.setJobId(job); context.setProjectId("project-1");
        context.setWorkspace(source.toString()); context.setParameters(Map.of("executionMode", "READ_WRITE"));
        context.setMetadata(Map.of("workspaceId", "workspace-1")); return context;
    }
    private Path initRepo() throws Exception {
        Path repo = temp.resolve("repo-" + System.nanoTime()); Files.createDirectories(repo);
        run(repo, "git", "init", "-b", "master"); Files.writeString(repo.resolve("tracked.txt"), "baseline"); run(repo, "git", "add", "."); run(repo, "git", "-c", "user.email=test@example.com", "-c", "user.name=Test", "commit", "-m", "baseline"); return repo;
    }
    private String git(Path cwd, String... args) throws Exception { return run(cwd, concat("git", args)); }
    private String run(Path cwd, String... command) throws Exception { Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start(); assertTrue(process.waitFor(30, TimeUnit.SECONDS)); String output = new String(process.getInputStream().readAllBytes()); assertEquals(0, process.exitValue(), output); return output; }
    private String[] concat(String first, String[] rest) { String[] value = new String[rest.length + 1]; value[0] = first; System.arraycopy(rest, 0, value, 1, rest.length); return value; }
}
