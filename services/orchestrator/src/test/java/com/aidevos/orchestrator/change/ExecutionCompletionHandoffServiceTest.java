package com.aidevos.orchestrator.change;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspace;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspacePromotionService;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceReview;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceStatus;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionCompletionHandoffServiceTest {
    @Test
    void projectsSuccessfulReadWriteExecutionOnce() {
        ChangeService changes = mock(ChangeService.class);
        ExecutionWorkspacePromotionService reviews = mock(ExecutionWorkspacePromotionService.class);
        TaskCenterService tasks = mock(TaskCenterService.class);
        ExecutionWorkspace workspace = new ExecutionWorkspace("ew-1", "task-1", "project-1", "source-1",
            "/source", "/execution", "GIT_WORKTREE", "ai-dev-os/task/task-1",
            ExecutionWorkspaceStatus.COMPLETED, "base", Instant.now(), Instant.now());
        TaskRecord task = mock(TaskRecord.class);
        when(tasks.getTask("task-1")).thenReturn(Optional.of(task));
        when(task.getStatus()).thenReturn(TaskStatus.SUCCESS);
        when(task.getExecutionMode()).thenReturn(ExecutionMode.READ_WRITE);
        when(reviews.findWorkspace("task-1")).thenReturn(workspace);
        ExecutionWorkspaceReview review = new ExecutionWorkspaceReview();
        review.setCompleteness("COMPLETE");
        review.getChangedFiles().add("src/New.java");
        review.setDiff("+class New {}\n");
        when(reviews.review("task-1")).thenReturn(review);
        when(changes.findByExecution("task-1", "exec-1")).thenReturn(Optional.empty());
        when(changes.findByWorkspace("task-1", "ew-1")).thenReturn(Optional.empty());
        ExecutionCompletionHandoffService service = new ExecutionCompletionHandoffService(changes, reviews, tasks, AuditService.noop());

        assertTrue(service.project("task-1", "exec-1").isPresent());
        verify(changes, times(1)).save(any(ChangeSet.class));
    }

    @Test
    void readOnlyTaskDoesNotProject() {
        TaskCenterService tasks = mock(TaskCenterService.class);
        TaskRecord task = mock(TaskRecord.class);
        when(tasks.getTask("task-1")).thenReturn(Optional.of(task));
        when(task.getStatus()).thenReturn(TaskStatus.SUCCESS);
        when(task.getExecutionMode()).thenReturn(ExecutionMode.READ_ONLY);
        ExecutionWorkspacePromotionService reviews = mock(ExecutionWorkspacePromotionService.class);
        ChangeService changes = mock(ChangeService.class);
        assertTrue(new ExecutionCompletionHandoffService(changes, reviews, tasks, AuditService.noop())
            .project("task-1", "exec-1").isEmpty());
        verifyNoInteractions(changes, reviews);
    }
}
