package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanApprovalStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresHumanApprovalRepositoryTest {

	private final PostgresDocumentStore store = mock(PostgresDocumentStore.class);
	private final PostgresHumanApprovalRepository repository =
		new PostgresHumanApprovalRepository(store);

	@Test
	void savesApprovalIntoRepositoryDocuments() {
		HumanApproval approval = approval(HumanApprovalStatus.PENDING);

		repository.save(approval);

		verify(store).put("human-approval", "gate-1",
			PersistenceSnapshots.HumanApproval.of(approval), "task:task-1");
	}

	@Test
	void pendingDecisionSurvivesReload() {
		stubGet(approval(HumanApprovalStatus.PENDING));

		assertEquals(HumanApprovalStatus.PENDING, repository.get("gate-1").getStatus());
	}

	@Test
	void approvedDecisionSurvivesReload() {
		HumanApproval approved = approval(HumanApprovalStatus.APPROVED);
		approved.approve("reviewer", "ok");
		stubGet(approved);

		HumanApproval restored = repository.get("gate-1");

		assertEquals(HumanApprovalStatus.APPROVED, restored.getStatus());
		assertEquals("reviewer", restored.getReviewer());
	}

	@Test
	void rejectedDecisionSurvivesReload() {
		HumanApproval rejected = approval(HumanApprovalStatus.REJECTED);
		rejected.reject("reviewer", "no");
		stubGet(rejected);

		HumanApproval restored = repository.get("gate-1");

		assertEquals(HumanApprovalStatus.REJECTED, restored.getStatus());
		assertEquals("no", restored.getComment());
	}

	@Test
	void missingApprovalReturnsNull() {
		when(store.get("human-approval", "gate-x", PersistenceSnapshots.HumanApproval.class))
			.thenReturn(null);

		assertNull(repository.get("gate-x"));
	}

	@Test
	void listsByTaskThroughSecondaryKey() {
		when(store.allBySecondary("human-approval", "task:task-1",
			PersistenceSnapshots.HumanApproval.class)).thenReturn(List.of(
				PersistenceSnapshots.HumanApproval.of(approval(HumanApprovalStatus.APPROVED))));

		List<HumanApproval> approvals = repository.listByTask("task-1");

		assertEquals(1, approvals.size());
		assertEquals(HumanApprovalStatus.APPROVED, approvals.getFirst().getStatus());
		verify(store).allBySecondary("human-approval", "task:task-1",
			PersistenceSnapshots.HumanApproval.class);
	}

	private void stubGet(HumanApproval approval) {
		when(store.get("human-approval", "gate-1", PersistenceSnapshots.HumanApproval.class))
			.thenReturn(PersistenceSnapshots.HumanApproval.of(approval));
	}

	private HumanApproval approval(HumanApprovalStatus status) {
		return new HumanApproval("gate-1", "task-1", null, null, "step-1", status,
			"plan-scheduler", null, null, Instant.parse("2026-08-20T00:00:00Z"), null);
	}
}
