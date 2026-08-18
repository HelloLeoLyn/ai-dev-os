package com.aidevos.orchestrator.execution.workspace;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExecutionWorkspacePromotionService {
    private static final long MAX_REVIEW_FILE_BYTES = 262_144;
    private final ExecutionWorkspaceRepository repository;
    private final WorkspaceService workspaceService;
    private final CommandExecutor commands;
    private final AuditService auditService;
    private final ExecutionRecordManager executionRecords;

    public ExecutionWorkspacePromotionService(ExecutionWorkspaceRepository repository,
            WorkspaceService workspaceService, CommandExecutor commands) {
        this(repository, workspaceService, commands, AuditService.noop(), null);
    }

    @Autowired
    public ExecutionWorkspacePromotionService(ExecutionWorkspaceRepository repository,
            WorkspaceService workspaceService, CommandExecutor commands, AuditService auditService,
            ExecutionRecordManager executionRecords) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.commands = commands;
        this.auditService = auditService;
        this.executionRecords = executionRecords;
    }

    public synchronized ExecutionWorkspaceReview review(String taskId) {
        ExecutionWorkspace workspace = requiredWorkspace(taskId);
        ExecutionWorkspaceReview review = new ExecutionWorkspaceReview();
        review.setTaskId(taskId);
        review.setStatus(workspace.getStatus().name());
        review.setSourceWorkspace(workspace.getSourceWorkspace());
        review.setExecutionWorkspace(workspace.getExecutionWorkspace());
        review.setBaseRevision(workspace.getBaseRevision());
        try {
            Path source = trustedSource(workspace);
            Path execution = existing(workspace.getExecutionWorkspace(), "EXECUTION_WORKSPACE_MISSING");
            ensureSameRepository(source, execution);
            String sourceRevision = git(source, List.of("git", "rev-parse", "HEAD"), "source revision").trim();
            review.setSourceRevision(sourceRevision);
            review.setDiff(git(execution, List.of("git", "diff", "--binary", workspace.getBaseRevision()), "execution diff"));
            review.setChangeStat(git(execution, List.of("git", "diff", "--stat", workspace.getBaseRevision()), "change stat"));
            review.setDiffCheck(git(execution, List.of("git", "diff", "--check", workspace.getBaseRevision()), "diff check"));
            review.setChangedFiles(parseLines(git(execution, List.of("git", "diff", "--name-only", workspace.getBaseRevision()), "changed files")));
            List<String> untracked = untracked(execution);
            review.setUntrackedFiles(untracked);
            appendUntrackedDiff(review, execution, untracked);
            if (executionRecords != null) {
                executionRecords.getAll().stream().filter(r -> taskId.equals(r.getTaskId()))
                    .flatMap(r -> r.getArtifacts().stream()).map(a -> a.getName())
                    .filter(name -> name != null && !name.isBlank()).distinct().forEach(review.getArtifacts()::add);
            }
            review.getChangedFiles().addAll(review.getUntrackedFiles());
            if (review.getIncompleteReasons().isEmpty()) review.setCompleteness("COMPLETE");
            auditService.promotionFlow("REVIEW_READY", workspace, sourceRevision, null);
            return review;
        }
        catch (PromotionException ex) {
            review.setErrorCode(ex.getErrorCode()); review.setReason(ex.getMessage());
            review.getIncompleteReasons().add(ex.getErrorCode() + ":" + ex.getMessage());
            return review;
        }
    }

    public synchronized ExecutionWorkspace promote(String taskId) {
        ExecutionWorkspace workspace = requiredWorkspace(taskId);
        if (workspace.getStatus() == ExecutionWorkspaceStatus.PROMOTED) return workspace;
        if (workspace.getStatus() != ExecutionWorkspaceStatus.COMPLETED
                && workspace.getStatus() != ExecutionWorkspaceStatus.PROMOTION_FAILED) {
            throw new PromotionException("REVIEW_NOT_READY", "Execution workspace is not ready for promotion");
        }
        ExecutionWorkspaceReview review = review(taskId);
        if (!review.isComplete()) {
            throw new PromotionException("REVIEW_INCOMPLETE", "Review does not contain a complete change set: "
                + String.join(", ", review.getIncompleteReasons()));
        }
        workspace.mark(ExecutionWorkspaceStatus.PROMOTING);
        repository.save(workspace);
        auditService.promotionFlow("PROMOTION_REQUESTED", workspace, null, null);
        Path patchFile = null;
        List<Path> copied = new ArrayList<>();
        boolean applied = false;
        try {
            Path source = trustedSource(workspace);
            Path execution = existing(workspace.getExecutionWorkspace(), "EXECUTION_WORKSPACE_MISSING");
            ensureSameRepository(source, execution);
            String currentRevision = git(source, List.of("git", "rev-parse", "HEAD"), "source revision").trim();
            if (!workspace.getBaseRevision().equals(currentRevision)) {
                throw new PromotionException("SOURCE_REVISION_CHANGED", "Source HEAD differs from execution base revision");
            }
            if (!git(source, List.of("git", "status", "--porcelain"), "source status").isBlank()) {
                throw new PromotionException("SOURCE_WORKSPACE_DIRTY", "Source workspace has uncommitted changes");
            }
            String patch = git(execution, List.of("git", "diff", "--binary", workspace.getBaseRevision()), "execution patch");
            List<String> untracked = untracked(execution);
            validateUntrackedPaths(source, execution, untracked);
            if (patch.isBlank() && untracked.isEmpty()) {
                throw new PromotionException("NO_CHANGES_TO_PROMOTE", "Execution workspace has no tracked or untracked changes");
            }
            auditService.promotionFlow("PROMOTION_VALIDATING", workspace, currentRevision, null);
            if (!patch.isBlank()) {
                patchFile = Files.createTempFile("ai-dev-os-promotion-", ".patch");
                Files.writeString(patchFile, patch, StandardCharsets.UTF_8);
                CommandResult check = gitResult(source, List.of("git", "apply", "--check", "--binary", patchFile.toString()), "patch check");
                if (!check.isSuccess()) throw new PromotionException("PATCH_CHECK_FAILED", check.getError());
                CommandResult apply = gitResult(source, List.of("git", "apply", "--binary", patchFile.toString()), "patch apply");
                if (!apply.isSuccess()) throw new PromotionException("PATCH_APPLY_FAILED", apply.getError());
                applied = true;
            }
            for (String relative : untracked) {
                Path target = source.resolve(relative).normalize();
                Files.createDirectories(target.getParent());
                Files.copy(execution.resolve(relative), target, StandardCopyOption.COPY_ATTRIBUTES);
                copied.add(target);
            }
            workspace.mark(ExecutionWorkspaceStatus.PROMOTED);
            workspace.setPromotedAt(Instant.now()); workspace.setPromotionErrorCode(null); workspace.setPromotionReason("promoted to source workspace");
            repository.save(workspace);
            auditService.promotionFlow("PROMOTION_SUCCEEDED", workspace, currentRevision, null);
            return workspace;
        }
        catch (PromotionException ex) {
            rollback(sourcePath(workspace), patchFile, copied, applied);
            fail(workspace, ex); throw ex;
        }
        catch (Exception ex) {
            rollback(sourcePath(workspace), patchFile, copied, applied);
            PromotionException failure = new PromotionException("PROMOTION_FAILED", ex.getMessage(), ex);
            fail(workspace, failure); throw failure;
        }
        finally {
            if (patchFile != null) try { Files.deleteIfExists(patchFile); } catch (IOException ignored) { }
        }
    }

    public synchronized ExecutionWorkspace reject(String taskId) {
        ExecutionWorkspace workspace = requiredWorkspace(taskId);
        if (workspace.getStatus() == ExecutionWorkspaceStatus.REJECTED) return workspace;
        if (workspace.getStatus() == ExecutionWorkspaceStatus.PROMOTED) {
            throw new PromotionException("ALREADY_PROMOTED", "Promoted workspace cannot be rejected");
        }
        if (workspace.getStatus() != ExecutionWorkspaceStatus.COMPLETED
                && workspace.getStatus() != ExecutionWorkspaceStatus.PROMOTION_FAILED) {
            throw new PromotionException("REVIEW_NOT_READY", "Execution workspace is not ready for rejection");
        }
        workspace.mark(ExecutionWorkspaceStatus.REJECTED); workspace.setRejectedAt(Instant.now());
        workspace.setPromotionReason("rejected by user"); repository.save(workspace);
        auditService.promotionFlow("WORKSPACE_REJECTED", workspace, null, null);
        return workspace;
    }

    private ExecutionWorkspace requiredWorkspace(String taskId) {
        ExecutionWorkspace value = repository.findByTaskId(taskId);
        if (value == null) throw new PromotionException("WORKSPACE_NOT_FOUND", "Execution workspace not found: " + taskId);
        return value;
    }

    private Path trustedSource(ExecutionWorkspace workspace) {
        Optional<Workspace> registered = workspaceService.getWorkspace(workspace.getWorkspaceId());
        if (registered.isEmpty()) throw new PromotionException("SOURCE_WORKSPACE_UNTRUSTED", "Registered source workspace not found");
        try {
            Path source = Path.of(registered.get().getPath()).toRealPath();
            if (!source.toString().equals(workspace.getSourceWorkspace())) throw new PromotionException("SOURCE_WORKSPACE_UNTRUSTED", "Source workspace path changed");
            return source;
        } catch (IOException ex) { throw new PromotionException("SOURCE_WORKSPACE_UNTRUSTED", "Source workspace is unavailable", ex); }
    }

    private Path sourcePath(ExecutionWorkspace workspace) {
        try { return Path.of(workspace.getSourceWorkspace()).toRealPath(); } catch (Exception ex) { return Path.of(workspace.getSourceWorkspace()); }
    }

    private Path existing(String value, String code) {
        if (value == null) throw new PromotionException(code, "Workspace path is missing");
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new PromotionException(code, "Workspace directory is missing: " + path);
        return path;
    }

    private void ensureSameRepository(Path source, Path execution) {
        String sourceGit = git(source, List.of("git", "rev-parse", "--git-common-dir"), "source repository").trim();
        String executionGit = git(execution, List.of("git", "rev-parse", "--git-common-dir"), "execution repository").trim();
        try {
            if (!repositoryIdentity(source, sourceGit).equals(repositoryIdentity(execution, executionGit))) {
                throw new PromotionException("REPOSITORY_MISMATCH", "Source and execution workspaces are different repositories");
            }
        } catch (IOException ex) { throw new PromotionException("REPOSITORY_MISMATCH", "Repository identity cannot be verified", ex); }
    }

    private Path repositoryIdentity(Path cwd, String value) throws IOException {
        Path path = Path.of(value);
        if (!path.isAbsolute()) path = cwd.resolve(path);
        return path.toAbsolutePath().normalize().toRealPath();
    }

    private List<String> untracked(Path execution) {
        String raw = git(execution, List.of("git", "ls-files", "--others", "--exclude-standard", "-z"), "untracked files");
        return Arrays.stream(raw.split("\\u0000", -1)).filter(value -> !value.isBlank()).toList();
    }

    private void appendUntrackedDiff(ExecutionWorkspaceReview review, Path execution, List<String> files) {
        StringBuilder additions = new StringBuilder();
        Path root;
        try {
            root = execution.toRealPath();
        }
        catch (IOException exception) {
            review.getIncompleteReasons().add("EXECUTION_WORKSPACE_UNAVAILABLE");
            return;
        }
        for (String relative : files) {
            try {
                Path relativePath = Path.of(relative);
                if (relativePath.isAbsolute() || relativePath.getNameCount() == 0
                        || relativePath.normalize().startsWith(Path.of(".."))) {
                    review.getIncompleteReasons().add("UNSAFE_UNTRACKED_PATH:" + relative);
                    continue;
                }
                Path file = root.resolve(relativePath).normalize();
                if (!file.startsWith(root) || Files.isSymbolicLink(file)
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    review.getIncompleteReasons().add("UNSAFE_UNTRACKED_PATH:" + relative);
                    continue;
                }
                long size = Files.size(file);
                if (size > MAX_REVIEW_FILE_BYTES) {
                    review.getIncompleteReasons().add("UNTRACKED_FILE_TOO_LARGE:" + relative);
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                String content = decodeReviewText(bytes);
                if (content == null) {
                    review.getIncompleteReasons().add("UNTRACKED_BINARY_FILE:" + relative);
                    continue;
                }
                additions.append(unifiedDiff(relative, content));
            }
            catch (IOException | RuntimeException exception) {
                review.getIncompleteReasons().add("UNTRACKED_FILE_READ_FAILED:" + relative);
            }
        }
        if (!additions.isEmpty()) {
            String tracked = review.getDiff() == null ? "" : review.getDiff();
            review.setDiff(tracked + (tracked.isEmpty() || tracked.endsWith("\n") ? "" : "\n") + additions);
        }
    }

    private String decodeReviewText(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return null;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return text;
        }
        catch (java.nio.charset.CharacterCodingException exception) {
            return null;
        }
    }

    private String unifiedDiff(String relative, String content) {
        String[] lines = content.split("\\n", -1);
        int count = content.isEmpty() ? 0 : (content.endsWith("\n") ? lines.length - 1 : lines.length);
        StringBuilder diff = new StringBuilder();
        diff.append("--- /dev/null\n+++ b/").append(relative).append("\n");
        diff.append("@@ -0,0 +1,").append(count).append(" @@\n");
        for (int index = 0; index < count; index++) diff.append('+').append(lines[index]).append('\n');
        if (!content.isEmpty() && !content.endsWith("\n")) diff.append("\\ No newline at end of file\n");
        return diff.toString();
    }

    private void validateUntrackedPaths(Path source, Path execution, List<String> files) {
        for (String relative : files) {
            Path from = execution.resolve(relative).normalize(); Path to = source.resolve(relative).normalize();
            if (!from.startsWith(execution) || !to.startsWith(source) || Files.isSymbolicLink(from) || !Files.isRegularFile(from)) {
                throw new PromotionException("UNTRACKED_PATH_INVALID", "Unsafe untracked file: " + relative);
            }
            if (Files.exists(to)) throw new PromotionException("UNTRACKED_TARGET_EXISTS", "Source target already exists: " + relative);
        }
    }

    private void rollback(Path source, Path patch, List<Path> copied, boolean applied) {
        copied.stream().sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
        if (applied && patch != null && Files.exists(source)) {
            try { gitResult(source, List.of("git", "apply", "--reverse", "--binary", patch.toString()), "patch rollback"); } catch (RuntimeException ignored) { }
        }
    }

    private void fail(ExecutionWorkspace workspace, PromotionException ex) {
        workspace.mark(ExecutionWorkspaceStatus.PROMOTION_FAILED); workspace.setPromotionErrorCode(ex.getErrorCode()); workspace.setPromotionReason(ex.getMessage()); repository.save(workspace);
        auditService.promotionFlow("PROMOTION_FAILED", workspace, null, ex.getErrorCode());
    }

    private String git(Path cwd, List<String> command, String label) {
        CommandResult result = gitResult(cwd, command, label);
        return result.getOutput() == null ? "" : result.getOutput();
    }

    private CommandResult gitResult(Path cwd, List<String> command, String label) {
        CommandOptions options = new CommandOptions(); options.setCommand(command); options.setWorkingDirectory(cwd.toString()); options.setTimeout(Duration.ofMinutes(2));
        CommandResult result = commands.execute(options);
        if (!result.isSuccess() && !"patch check".equals(label) && !"patch apply".equals(label) && !"patch rollback".equals(label)) throw new PromotionException("GIT_VALIDATION_FAILED", label + " failed: " + result.getError());
        return result;
    }

    private List<String> parseLines(String value) { return Arrays.stream(value.split("\\R")).map(String::trim).filter(s -> !s.isBlank()).toList(); }
}
