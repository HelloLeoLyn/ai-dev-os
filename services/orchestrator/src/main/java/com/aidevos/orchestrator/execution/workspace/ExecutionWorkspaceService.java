package com.aidevos.orchestrator.execution.workspace;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExecutionWorkspaceService {
    private final ExecutionWorkspaceRepository repository;
    private final WorkspaceService sourceWorkspaces;
    private final CommandExecutor commandExecutor;
    private final CodingWorkspaceProperties properties;
    private final AuditService auditService;
    private final TaskWorkspaceTrustService trustService;

    public ExecutionWorkspaceService(ExecutionWorkspaceRepository repository, WorkspaceService sourceWorkspaces,
            CommandExecutor commandExecutor, CodingWorkspaceProperties properties) {
        this(repository, sourceWorkspaces, commandExecutor, properties, AuditService.noop(), null);
    }
    public ExecutionWorkspaceService(ExecutionWorkspaceRepository repository, WorkspaceService sourceWorkspaces,
            CommandExecutor commandExecutor, CodingWorkspaceProperties properties, AuditService auditService) {
        this(repository, sourceWorkspaces, commandExecutor, properties, auditService, null);
    }
    @Autowired
    public ExecutionWorkspaceService(ExecutionWorkspaceRepository repository, WorkspaceService sourceWorkspaces,
            CommandExecutor commandExecutor, CodingWorkspaceProperties properties, AuditService auditService,
            TaskWorkspaceTrustService trustService) {
        this.repository=repository; this.sourceWorkspaces=sourceWorkspaces; this.commandExecutor=commandExecutor; this.properties=properties; this.auditService=auditService; this.trustService=trustService;
    }

    public synchronized ExecutionWorkspace ensureReady(ExecutionContext context) {
        if (!"READ_WRITE".equalsIgnoreCase(String.valueOf(context.getParameters().get("executionMode")))) return null;
        String taskId = required(context.getTaskId(), "Task ID");
        ExecutionWorkspace existing = repository.findByTaskId(taskId);
        if (existing != null) {
            if (existing.getExecutionBranch() == null || existing.getExecutionBranch().isBlank()) {
                throw new IllegalStateException("Execution workspace has no trusted execution branch: " + taskId);
            }
            if (existing.getStatus() == ExecutionWorkspaceStatus.READY || existing.getStatus() == ExecutionWorkspaceStatus.COMPLETED
                    || existing.getStatus() == ExecutionWorkspaceStatus.PROMOTED) {
                try {
                    validate(existing);
                    auditService.executionWorkspaceFlow("EXECUTION_WORKSPACE_REUSED", taskId, context.getJobId(), existing.getId(), existing.getSourceWorkspace(), existing.getExecutionWorkspace(), existing.getStrategy(), existing.getBaseRevision(), existing.getStatus().name(), "existing task workspace", null);
                    return existing;
                }
                catch (RuntimeException ex) {
                    existing.mark(ExecutionWorkspaceStatus.FAILED); repository.save(existing);
                    throw ex;
                }
            }
            if (existing.getStatus() == ExecutionWorkspaceStatus.CREATING && recover(existing)) {
                existing.mark(ExecutionWorkspaceStatus.READY); repository.save(existing); auditService.executionWorkspaceFlow("EXECUTION_WORKSPACE_REUSED", taskId, context.getJobId(), existing.getId(), existing.getSourceWorkspace(), existing.getExecutionWorkspace(), existing.getStrategy(), existing.getBaseRevision(), existing.getStatus().name(), "recovered creating workspace", null); return existing;
            }
            existing.mark(ExecutionWorkspaceStatus.FAILED); repository.save(existing);
            throw new IllegalStateException("Execution workspace is not recoverable: " + taskId);
        }
        String sourceId = required(string(context.getMetadata(), "workspaceId"), "Workspace ID");
        Workspace source = sourceWorkspaces.getWorkspace(sourceId).orElseThrow(() -> new IllegalArgumentException("Source workspace not found"));
        Path sourcePath = real(source.getPath());
        if (trustService != null) trustService.requireTrustedWorkspace(context, sourcePath);
        String base = git(sourcePath, List.of("git", "rev-parse", "HEAD"), "base revision").trim();
        if (base.isBlank()) throw new IllegalStateException("Source workspace has no committed HEAD");
        Path root = Path.of(properties.getExecutionWorkspaceRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(safe(taskId)).normalize();
        if (!target.startsWith(root) || Files.exists(target)) throw new IllegalStateException("Execution workspace path collision");
        String executionBranch = branch(taskId);
        if (executionBranch.equals(source.getBranch())) {
            throw new IllegalStateException("Execution branch must differ from source branch");
        }
        String id = "execution-workspace-" + UUID.nameUUIDFromBytes(("task:" + taskId)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ExecutionWorkspace value = new ExecutionWorkspace(id, taskId, context.getProjectId(), sourceId,
            sourcePath.toString(), target.toString(), "GIT_WORKTREE", executionBranch,
            ExecutionWorkspaceStatus.CREATING, base, Instant.now(), Instant.now());
        repository.save(value);
        auditService.executionWorkspaceFlow("EXECUTION_WORKSPACE_CREATING", taskId, context.getJobId(), id, value.getSourceWorkspace(), value.getExecutionWorkspace(), value.getStrategy(), base, value.getStatus().name(), "creating git worktree", null);
        try {
            try { Files.createDirectories(root); } catch (java.io.IOException ex) { throw new IllegalStateException("Execution workspace root unavailable", ex); }
            git(sourcePath, List.of("git", "worktree", "add", "-b", executionBranch,
                target.toString(), base), "create worktree");
            value.mark(ExecutionWorkspaceStatus.READY); repository.save(value); auditService.executionWorkspaceFlow("EXECUTION_WORKSPACE_READY", taskId, context.getJobId(), id, value.getSourceWorkspace(), value.getExecutionWorkspace(), value.getStrategy(), base, value.getStatus().name(), "git worktree ready", null); return value;
        } catch (RuntimeException ex) {
            value.mark(ExecutionWorkspaceStatus.FAILED); repository.save(value); auditService.executionWorkspaceFlow("EXECUTION_WORKSPACE_FAILED", taskId, context.getJobId(), id, value.getSourceWorkspace(), value.getExecutionWorkspace(), value.getStrategy(), base, value.getStatus().name(), ex.getMessage(), "WORKTREE_CREATE_FAILED"); throw ex;
        }
    }
    public ExecutionWorkspace findByTaskId(String taskId) { return repository.findByTaskId(taskId); }

    public synchronized void markCompleted(String taskId, String jobId) {
        markTerminal(taskId, jobId, ExecutionWorkspaceStatus.COMPLETED, "execution completed");
    }

    public synchronized void markFailed(String taskId, String jobId, String reason) {
        markTerminal(taskId, jobId, ExecutionWorkspaceStatus.FAILED, reason == null ? "execution failed" : reason);
    }

    private void markTerminal(String taskId, String jobId, ExecutionWorkspaceStatus status, String reason) {
        if (taskId == null || taskId.isBlank()) return;
        ExecutionWorkspace value = repository.findByTaskId(taskId);
        if (value == null || value.getStatus() == ExecutionWorkspaceStatus.FAILED) return;
        value.mark(status);
        repository.save(value);
        auditService.executionWorkspaceFlow("EXECUTION_WORKSPACE_" + status.name(), taskId, jobId,
            value.getId(), value.getSourceWorkspace(), value.getExecutionWorkspace(), value.getStrategy(),
            value.getBaseRevision(), status.name(), reason, null);
    }

    private boolean recover(ExecutionWorkspace value) {
        try {
            return Files.isDirectory(Path.of(value.getExecutionWorkspace()))
                && value.getExecutionBranch().equals(git(Path.of(value.getExecutionWorkspace()), List.of("git", "branch", "--show-current"), "worktree branch").trim())
                && value.getBaseRevision().equals(git(Path.of(value.getExecutionWorkspace()), List.of("git", "rev-parse", "HEAD"), "worktree revision").trim());
        }
        catch (RuntimeException ex) {
            return false;
        }
    }
    private void validate(ExecutionWorkspace value) { Path path = Path.of(value.getExecutionWorkspace()); if (!Files.isDirectory(path) || !value.getExecutionBranch().equals(git(path, List.of("git", "branch", "--show-current"), "worktree branch").trim()) || !value.getBaseRevision().equals(git(path, List.of("git", "rev-parse", "HEAD"), "worktree revision").trim())) throw new IllegalStateException("Execution workspace validation failed"); }
    private String git(Path cwd, List<String> command, String label) {
        CommandOptions options=new CommandOptions(); options.setCommand(command); options.setWorkingDirectory(cwd.toString()); options.setTimeout(Duration.ofMinutes(2));
        CommandResult result=commandExecutor.execute(options); if (!result.isSuccess()) throw new IllegalStateException(label + " failed: " + result.getError()); return result.getOutput();
    }
    private Path real(String value){try{return Path.of(value).toRealPath();}catch(Exception e){throw new IllegalArgumentException("Source workspace unavailable",e);}}
    private String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");return value;}
    private String string(java.util.Map<String,Object> map,String key){Object value=map.get(key);return value instanceof String s&&!s.isBlank()?s:null;}
    private String safe(String value){String normalized=value.replaceAll("[^A-Za-z0-9._-]", "_"); if(normalized.isBlank()||normalized.equals(".")||normalized.equals(".."))throw new IllegalArgumentException("Invalid task id"); return normalized;}
    private String branch(String taskId) {
        String value = "ai-dev-os/task/" + safe(taskId);
        if (value.equals("main") || value.equals("master")) {
            throw new IllegalArgumentException("Invalid execution branch");
        }
        return value;
    }
}
