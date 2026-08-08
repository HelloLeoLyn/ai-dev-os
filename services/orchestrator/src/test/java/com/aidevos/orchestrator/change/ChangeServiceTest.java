package com.aidevos.orchestrator.change;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.WorkspaceStatus;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit verification of change management: git snapshot capture, review state
 * machine (CREATED -> REVIEWING -> APPROVED | REJECTED), task scoping and the
 * CHANGE_* audit trail.
 */
class ChangeServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private InMemoryChangeRepository repository;
	private WorkspaceService workspaceService;
	private InMemoryAuditRepository auditRepository;
	private ChangeService changeService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryChangeRepository();
		workspaceService = mock(WorkspaceService.class);
		auditRepository = new InMemoryAuditRepository();
		changeService = new ChangeService(repository, workspaceService,
			new AuditService(auditRepository));

		when(workspaceService.getWorkspace("workspace-1")).thenReturn(
			Optional.of(new Workspace("workspace-1", "project-a", "/tmp/repo", "main",
				WorkspaceStatus.READY, NOW, NOW)));
		when(workspaceService.checkGitStatus("workspace-1")).thenReturn(
			new GitStatus("main", 1, 2, 0));
		when(workspaceService.getGitDiff("workspace-1")).thenReturn(
			new GitDiff(3, 5, 1, "3 files changed, 5 insertions(+), 1 deletion(-)"));
		when(workspaceService.getGitDiffContent("workspace-1")).thenReturn(
			"diff --git a/a.txt b/a.txt\n@@ -1 +1,2 @@\n one\n+two\n");
	}

	@Test
	void shouldCreateChangeWithGitSnapshot() {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");

		assertTrue(change.getChangeId().startsWith("change-"));
		assertEquals("task-1", change.getTaskId());
		assertEquals("workspace-1", change.getWorkspaceId());
		assertEquals("project-a", change.getProjectId());
		assertEquals("exec-1", change.getExecutionId());
		assertEquals("main", change.getBranch());
		assertEquals(ChangeStatus.CREATED, change.getStatus());
		assertEquals(3, change.getFilesChanged());
		assertEquals(5, change.getInsertions());
		assertEquals(1, change.getDeletions());
		assertEquals(1, change.getModified());
		assertEquals(2, change.getAdded());
		assertEquals(0, change.getDeleted());
		assertTrue(change.getDiff().contains("a.txt"));
		assertTrue(change.getDiffStat().contains("3 files changed"));
		assertEquals(change, repository.get(change.getChangeId()));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CHANGE_CREATED
			&& "task-1".equals(event.taskId())
			&& change.getChangeId().equals(event.aggregateId())));
	}

	@Test
	void shouldMoveThroughReviewLifecycleToApproved() {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");

		changeService.startReview(change.getChangeId());
		assertEquals(ChangeStatus.REVIEWING, changeService.getChange(change.getChangeId())
			.orElseThrow().getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CHANGE_REVIEWING
			&& "task-1".equals(event.taskId())));

		changeService.approve(change.getChangeId(), "user-1");
		ChangeSet approved = changeService.getChange(change.getChangeId()).orElseThrow();
		assertEquals(ChangeStatus.APPROVED, approved.getStatus());
		assertEquals("user-1", approved.getReviewedBy());
		assertNotNull(approved.getReviewedAt());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CHANGE_APPROVED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldRejectChange() {
		ChangeSet change = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");

		changeService.startReview(change.getChangeId());
		changeService.reject(change.getChangeId(), "user-2");

		ChangeSet rejected = changeService.getChange(change.getChangeId()).orElseThrow();
		assertEquals(ChangeStatus.REJECTED, rejected.getStatus());
		assertEquals("user-2", rejected.getReviewedBy());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CHANGE_REJECTED
			&& "task-1".equals(event.taskId())));
	}

	@Test
	void shouldRejectInvalidTransitions() {
		ChangeSet created = changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");
		assertThrows(IllegalStateException.class,
			() -> changeService.approve(created.getChangeId(), "user-1"));
		assertThrows(IllegalStateException.class,
			() -> changeService.reject(created.getChangeId(), "user-1"));

		changeService.startReview(created.getChangeId());
		changeService.approve(created.getChangeId(), "user-1");
		assertThrows(IllegalStateException.class,
			() -> changeService.startReview(created.getChangeId()));
	}

	@Test
	void shouldListChangesByTaskNewestFirst() {
		changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-1");
		changeService.createChange("task-1", "workspace-1", "project-a",
			"exec-2");

		List<ChangeSet> changes = changeService.getChangesByTask("task-1");
		assertEquals(2, changes.size());
		assertTrue(changes.get(0).getCreatedAt().compareTo(changes.get(1).getCreatedAt()) >= 0);
		assertTrue(changes.stream().anyMatch(change -> "exec-1".equals(change.getExecutionId())));
		assertTrue(changes.stream().anyMatch(change -> "exec-2".equals(change.getExecutionId())));
		assertEquals(0, changeService.getChangesByTask("other-task").size());
	}

	@Test
	void shouldReturnEmptyAndDiffForUnknownChange() {
		assertFalse(changeService.getChange("missing").isPresent());
		assertThrows(ResourceNotFoundException.class, () -> changeService.getDiff("missing"));
		assertThrows(ResourceNotFoundException.class, () -> changeService.approve("missing", "u"));
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}
}
