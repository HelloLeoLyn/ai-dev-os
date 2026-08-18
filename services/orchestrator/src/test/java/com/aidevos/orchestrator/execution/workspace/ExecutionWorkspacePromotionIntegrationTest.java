package com.aidevos.orchestrator.execution.workspace;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionWorkspacePromotionIntegrationTest {
    @TempDir Path temp;

    @Test
    void promotesTrackedAndUntrackedChanges() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-promote");
        ExecutionWorkspace execution = fixture.ready();
        Path worktree = Path.of(execution.getExecutionWorkspace());
        Files.writeString(worktree.resolve("tracked.txt"), "new\n");
        Files.writeString(worktree.resolve("new.txt"), "created\n");
        fixture.complete();
        assertEquals("", run(source, "git", "status", "--porcelain"));

        ExecutionWorkspace promoted = fixture.service.promote("task-promote");

        assertEquals(ExecutionWorkspaceStatus.PROMOTED, promoted.getStatus());
        assertEquals("new\n", Files.readString(source.resolve("tracked.txt")));
        assertEquals("created\n", Files.readString(source.resolve("new.txt")));
        assertTrue(Files.isDirectory(worktree));
    }

    @Test
    void promotesUntrackedOnlyChangesWithoutApplyingEmptyPatch() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-untracked-only");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.writeString(worktree.resolve("new.txt"), "created\n");
        fixture.complete();

        ExecutionWorkspace promoted = fixture.service.promote("task-untracked-only");

        assertEquals(ExecutionWorkspaceStatus.PROMOTED, promoted.getStatus());
        assertEquals("created\n", Files.readString(source.resolve("new.txt")));
        assertEquals("?? new.txt\n", run(source, "git", "status", "--porcelain"));
    }

    @Test
    void noTrackedOrUntrackedChangesHaveExplicitFailure() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-no-changes");
        fixture.ready();
        fixture.complete();

        PromotionException error = assertThrows(PromotionException.class,
            () -> fixture.service.promote("task-no-changes"));

        assertEquals("NO_CHANGES_TO_PROMOTE", error.getErrorCode());
        assertEquals(ExecutionWorkspaceStatus.PROMOTION_FAILED,
            fixture.repository.findByTaskId("task-no-changes").getStatus());
        assertEquals("", run(source, "git", "status", "--porcelain"));
    }

    @Test
    void failedUntrackedOnlyPromotionCanRetryOnSameWorkspace() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-untracked-retry");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.writeString(worktree.resolve("new.txt"), "created\n");
        Files.writeString(source.resolve("tracked.txt"), "user\n");
        fixture.complete();

        PromotionException first = assertThrows(PromotionException.class,
            () -> fixture.service.promote("task-untracked-retry"));
        assertEquals("SOURCE_WORKSPACE_DIRTY", first.getErrorCode());
        assertEquals(ExecutionWorkspaceStatus.PROMOTION_FAILED,
            fixture.repository.findByTaskId("task-untracked-retry").getStatus());
        Files.writeString(source.resolve("tracked.txt"), "baseline\n");

        ExecutionWorkspace promoted = fixture.service.promote("task-untracked-retry");

        assertEquals(ExecutionWorkspaceStatus.PROMOTED, promoted.getStatus());
        assertEquals("created\n", Files.readString(source.resolve("new.txt")));
        assertEquals(worktree, Path.of(promoted.getExecutionWorkspace()));
    }

    @Test
    void unsafeUntrackedSymlinkIsBlockedWithoutSourceChange() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-unsafe-untracked");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.createSymbolicLink(worktree.resolve("unsafe.txt"), source.resolve("tracked.txt"));
        fixture.complete();

        PromotionException error = assertThrows(PromotionException.class,
            () -> fixture.service.promote("task-unsafe-untracked"));

        assertEquals("REVIEW_INCOMPLETE", error.getErrorCode());
        assertEquals("", run(source, "git", "status", "--porcelain"));
        assertFalse(Files.exists(source.resolve("unsafe.txt")));
    }

    @Test
    void existingUntrackedTargetIsBlockedWithoutOverwrite() throws Exception {
        Path source = initRepo();
        Files.writeString(source.resolve(".gitignore"), "conflict.txt\n");
        run(source, "git", "add", ".gitignore");
        commit(source, "ignore promotion conflict", null);
        Files.writeString(source.resolve("conflict.txt"), "user\n");

        Fixture fixture = fixture(source, "task-untracked-conflict");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.delete(worktree.resolve(".gitignore"));
        Files.writeString(worktree.resolve("conflict.txt"), "ai\n");
        fixture.complete();

        PromotionException error = assertThrows(PromotionException.class,
            () -> fixture.service.promote("task-untracked-conflict"));

        assertEquals("UNTRACKED_TARGET_EXISTS", error.getErrorCode());
        assertEquals("user\n", Files.readString(source.resolve("conflict.txt")));
        assertTrue(Files.exists(source.resolve("conflict.txt")));
    }

    @Test
    void reviewIncludesTrackedAndUntrackedDiffWithoutChangingSourceOrIndex() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-review");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.writeString(worktree.resolve("tracked.txt"), "reviewed\n");
        Files.writeString(worktree.resolve("new.txt"), "new file\nsecond line\n");
        fixture.complete();
        String sourceStatus = run(source, "git", "status", "--porcelain");
        String sourceIndex = run(source, "git", "write-tree");

        ExecutionWorkspaceReview review = fixture.service.review("task-review");

        assertEquals("COMPLETE", review.getCompleteness());
        assertTrue(review.getDiff().contains("tracked.txt"));
        assertTrue(review.getDiff().contains("--- /dev/null"));
        assertTrue(review.getDiff().contains("+++ b/new.txt"));
        assertTrue(review.getDiff().contains("+new file"));
        assertEquals(List.of("new.txt"), review.getUntrackedFiles());
        assertEquals(sourceStatus, run(source, "git", "status", "--porcelain"));
        assertEquals(sourceIndex, run(source, "git", "write-tree"));
    }

    @Test
    void binaryUntrackedFileMakesReviewIncompleteAndBlocksPromotion() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-review-binary");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.write(worktree.resolve("binary.bin"), new byte[] {0, 1, 2});
        fixture.complete();

        ExecutionWorkspaceReview review = fixture.service.review("task-review-binary");
        assertEquals("INCOMPLETE", review.getCompleteness());
        assertTrue(review.getIncompleteReasons().stream().anyMatch(reason -> reason.contains("BINARY")));
        PromotionException error = assertThrows(PromotionException.class,
            () -> fixture.service.promote("task-review-binary"));
        assertEquals("REVIEW_INCOMPLETE", error.getErrorCode());
        assertFalse(Files.exists(source.resolve("binary.bin")));
    }

    @Test
    void deletedTrackedFileIsIncludedInReviewDiff() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-review-delete");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.delete(worktree.resolve("tracked.txt"));
        fixture.complete();

        ExecutionWorkspaceReview review = fixture.service.review("task-review-delete");

        assertEquals("COMPLETE", review.getCompleteness());
        assertTrue(review.getDiff().contains("--- a/tracked.txt"));
        assertTrue(review.getDiff().contains("+++ /dev/null"));
    }

    @Test
    void oversizedUntrackedFileMakesReviewIncomplete() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-review-large");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.write(worktree.resolve("large.txt"), new byte[262_145]);
        fixture.complete();

        ExecutionWorkspaceReview review = fixture.service.review("task-review-large");

        assertEquals("INCOMPLETE", review.getCompleteness());
        assertTrue(review.getIncompleteReasons().stream().anyMatch(reason -> reason.contains("TOO_LARGE")));
    }

    @Test
    void dirtySourceIsBlockedWithoutChangingUserFiles() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-dirty");
        ExecutionWorkspace execution = fixture.ready();
        Files.writeString(Path.of(execution.getExecutionWorkspace()).resolve("tracked.txt"), "ai\n");
        Files.writeString(source.resolve("tracked.txt"), "user\n");
        fixture.complete();

        PromotionException error = assertThrows(PromotionException.class, () -> fixture.service.promote("task-dirty"));

        assertEquals("SOURCE_WORKSPACE_DIRTY", error.getErrorCode());
        assertEquals("user\n", Files.readString(source.resolve("tracked.txt")));
        assertEquals(ExecutionWorkspaceStatus.PROMOTION_FAILED, fixture.repository.findByTaskId("task-dirty").getStatus());
    }

    @Test
    void sourceHeadChangeIsBlocked() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-head");
        ExecutionWorkspace execution = fixture.ready();
        Files.writeString(Path.of(execution.getExecutionWorkspace()).resolve("tracked.txt"), "ai\n");
        commit(source, "source change", "source\n");
        fixture.complete();

        PromotionException error = assertThrows(PromotionException.class, () -> fixture.service.promote("task-head"));

        assertEquals("SOURCE_REVISION_CHANGED", error.getErrorCode());
        assertEquals("source\n", Files.readString(source.resolve("tracked.txt")));
    }

    @Test
    void promotionFailureCanBeRejectedWithoutChangingSource() throws Exception {
        Path source = initRepo();
        Fixture fixture = fixture(source, "task-failed-promote");
        Path worktree = Path.of(fixture.ready().getExecutionWorkspace());
        Files.writeString(worktree.resolve("tracked.txt"), "ai\n");
        commit(source, "source change", "source\n");
        fixture.complete();

        assertThrows(PromotionException.class, () -> fixture.service.promote("task-failed-promote"));
        assertEquals(ExecutionWorkspaceStatus.PROMOTION_FAILED, fixture.repository.findByTaskId("task-failed-promote").getStatus());
        ExecutionWorkspace rejected = fixture.service.reject("task-failed-promote");

        assertEquals(ExecutionWorkspaceStatus.REJECTED, rejected.getStatus());
        assertEquals("source\n", Files.readString(source.resolve("tracked.txt")));
        assertEquals("", run(source, "git", "status", "--porcelain"));
        assertTrue(Files.isDirectory(worktree));
    }

    @Test
    void rejectLeavesSourceUnchangedAndDuplicatePromoteIsIdempotent() throws Exception {
        Path source = initRepo();
        Fixture reject = fixture(source, "task-reject");
        ExecutionWorkspace rejectWorkspace = reject.ready();
        Files.writeString(Path.of(rejectWorkspace.getExecutionWorkspace()).resolve("tracked.txt"), "rejected\n");
        reject.complete();
        assertEquals(ExecutionWorkspaceStatus.REJECTED, reject.service.reject("task-reject").getStatus());
        assertEquals("baseline\n", Files.readString(source.resolve("tracked.txt")));
        assertTrue(Files.isDirectory(Path.of(rejectWorkspace.getExecutionWorkspace())));

        Fixture promote = fixture(source, "task-idempotent");
        ExecutionWorkspace worktree = promote.ready();
        Files.writeString(Path.of(worktree.getExecutionWorkspace()).resolve("tracked.txt"), "once\n");
        promote.complete();
        ExecutionWorkspace first = promote.service.promote("task-idempotent");
        ExecutionWorkspace second = promote.service.promote("task-idempotent");
        assertEquals(ExecutionWorkspaceStatus.PROMOTED, first.getStatus());
        assertEquals(first.getId(), second.getId());
        assertEquals("once\n", Files.readString(source.resolve("tracked.txt")));
        assertThrows(PromotionException.class, () -> promote.service.reject("task-idempotent"));

        Fixture ready = fixture(source, "task-ready-reject");
        ready.ready();
        assertThrows(PromotionException.class, () -> ready.service.reject("task-ready-reject"));
    }

    private Fixture fixture(Path source, String taskId) {
        WorkspaceService sourceService = mock(WorkspaceService.class);
        when(sourceService.getWorkspace("workspace-1")).thenReturn(Optional.of(new Workspace("workspace-1", "project-1",
            source.toString(), "master", WorkspaceStatus.READY, Instant.now(), Instant.now())));
        InMemoryExecutionWorkspaceRepository repository = new InMemoryExecutionWorkspaceRepository();
        CodingWorkspaceProperties properties = new CodingWorkspaceProperties(); properties.setExecutionWorkspaceRoot(temp.resolve("worktrees").toString());
        ExecutionWorkspaceService workspaceService = new ExecutionWorkspaceService(repository, sourceService, new CommandExecutor(), properties);
        ExecutionWorkspacePromotionService promotion = new ExecutionWorkspacePromotionService(repository, sourceService, new CommandExecutor());
        return new Fixture(taskId, source, repository, workspaceService, promotion);
    }

    private static final class Fixture {
        final String taskId; final Path source; final InMemoryExecutionWorkspaceRepository repository;
        final ExecutionWorkspaceService workspaceService; final ExecutionWorkspacePromotionService service;
        Fixture(String taskId, Path source, InMemoryExecutionWorkspaceRepository repository,
                ExecutionWorkspaceService workspaceService, ExecutionWorkspacePromotionService service) {
            this.taskId=taskId; this.source=source; this.repository=repository; this.workspaceService=workspaceService; this.service=service;
        }
        ExecutionWorkspace ready() {
            com.aidevos.orchestrator.execution.ExecutionContext context = new com.aidevos.orchestrator.execution.ExecutionContext();
            context.setTaskId(taskId); context.setProjectId("project-1"); context.setParameters(Map.of("executionMode", "READ_WRITE"));
            context.setMetadata(Map.of("workspaceId", "workspace-1"));
            return workspaceService.ensureReady(context);
        }
        void complete() { ExecutionWorkspace value=repository.findByTaskId(taskId); value.mark(ExecutionWorkspaceStatus.COMPLETED); repository.save(value); }
    }

    private Path initRepo() throws Exception {
        Path repo = temp.resolve("repo-" + System.nanoTime()); Files.createDirectories(repo);
        run(repo, "git", "init", "-b", "master"); Files.writeString(repo.resolve("tracked.txt"), "baseline\n");
        run(repo, "git", "add", "."); commit(repo, "baseline", null); return repo;
    }
    private void commit(Path repo, String message, String content) throws Exception {
        if (content != null) { Files.writeString(repo.resolve("tracked.txt"), content); run(repo, "git", "add", "."); }
        run(repo, "git", "-c", "user.email=test@example.com", "-c", "user.name=Test", "commit", "-m", message);
    }
    private String run(Path cwd, String... command) throws Exception { Process process=new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start(); assertTrue(process.waitFor(30, TimeUnit.SECONDS)); String output=new String(process.getInputStream().readAllBytes()); assertEquals(0, process.exitValue(), output); return output; }
}
