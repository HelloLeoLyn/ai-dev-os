package com.aidevos.orchestrator.change;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceReview;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.change.ChangeStatus;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Projects a successful isolated execution into the existing ChangeSet flow. */
@Service
public class ExecutionCompletionHandoffService {
    private final ChangeService changes;
    private final ExecutionWorkspacePromotionService reviews;
    private final TaskCenterService tasks;
    private final AuditService audit;

    @Autowired
    public ExecutionCompletionHandoffService(ChangeService changes,
            ExecutionWorkspacePromotionService reviews, @Lazy TaskCenterService tasks,
            AuditService audit) {
        this.changes = changes; this.reviews = reviews; this.tasks = tasks; this.audit = audit;
    }

    public synchronized Optional<ChangeSet> project(String taskId, String executionId) {
        if (taskId == null || taskId.isBlank()) return Optional.empty();
        TaskRecord task = tasks.getTask(taskId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.SUCCESS
                || task.getExecutionMode() != ExecutionMode.READ_WRITE) return Optional.empty();
        ExecutionWorkspace workspace = reviews.findWorkspace(taskId);
        if (workspace == null || workspace.getStatus() != ExecutionWorkspaceStatus.COMPLETED) return Optional.empty();
        String effectiveExecution = executionId == null || executionId.isBlank()
            ? workspace.getId() : executionId;
        Optional<ChangeSet> existing = changes.findByExecution(taskId, effectiveExecution);
        if (existing.isEmpty()) existing = changes.findByWorkspace(taskId, workspace.getId());
        if (existing.isPresent()) return existing;
        audit.changeEvent(EventType.CHANGESET_GENERATION_REQUESTED, taskId,
            "handoff:" + workspace.getId(), "", "PENDING", "ChangeSet generation requested", Map.of(
                "executionWorkspaceId", workspace.getId(), "executionId", effectiveExecution));
        try {
            ExecutionWorkspaceReview review = reviews.review(taskId);
            if (!review.isComplete()) {
                audit.changeEvent(EventType.CHANGESET_GENERATION_FAILED, taskId,
                    "handoff:" + workspace.getId(), "PENDING", "FAILED",
                    "ChangeSet generation failed", Map.of("error", String.join(",", review.getIncompleteReasons())));
                return Optional.empty();
            }
            if (review.getChangedFiles().isEmpty()) return Optional.empty();
            ChangeSet change = new ChangeSet("change-" + java.util.UUID.randomUUID(), taskId,
                workspace.getId(), workspace.getProjectId(), effectiveExecution,
                workspace.getExecutionBranch(), review.getDiff(), review.getChangeStat(),
                review.getChangedFiles().size(), insertions(review), deletions(review),
                review.getChangedFiles().size() - review.getUntrackedFiles().size(),
                review.getUntrackedFiles().size(), 0, java.time.Instant.now());
            changes.save(change);
            audit.changeEvent(EventType.CHANGESET_GENERATED, taskId, change.getChangeId(),
                "PENDING", ChangeStatus.CREATED.name(), "ChangeSet generated", Map.of(
                    "executionWorkspaceId", workspace.getId(), "executionId", effectiveExecution,
                    "filesChanged", change.getFilesChanged()));
            return Optional.of(change);
        } catch (RuntimeException ex) {
            audit.changeEvent(EventType.CHANGESET_GENERATION_FAILED, taskId,
                "handoff:" + workspace.getId(), "PENDING", "FAILED", "ChangeSet generation failed", Map.of(
                    "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return Optional.empty();
        }
    }

    public Optional<ChangeSet> retry(String taskId) {
        ExecutionWorkspace workspace = reviews.findWorkspace(taskId);
        return project(taskId, workspace == null ? null : workspace.getId());
    }

    private int insertions(ExecutionWorkspaceReview review) {
        return review.getDiff() == null ? 0 : (int) review.getDiff().lines()
            .filter(line -> line.startsWith("+") && !line.startsWith("+++" )).count();
    }
    private int deletions(ExecutionWorkspaceReview review) {
        return review.getDiff() == null ? 0 : (int) review.getDiff().lines()
            .filter(line -> line.startsWith("-") && !line.startsWith("---" )).count();
    }
}
