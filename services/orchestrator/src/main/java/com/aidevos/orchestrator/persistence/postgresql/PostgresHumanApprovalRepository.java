package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;

import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanApprovalRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the human approval repository backed by the
 * shared repository_documents store. PENDING / APPROVED / REJECTED decisions
 * survive service restarts and repository reloads.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresHumanApprovalRepository implements HumanApprovalRepository {

	private static final String TYPE = "human-approval";

	private final PostgresDocumentStore store;

	PostgresHumanApprovalRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(HumanApproval approval) {
		store.put(TYPE, approval.getApprovalId(),
			PersistenceSnapshots.HumanApproval.of(approval), "task:" + approval.getTaskId());
	}

	@Override
	public HumanApproval get(String approvalId) {
		PersistenceSnapshots.HumanApproval snapshot = store.get(TYPE, approvalId,
			PersistenceSnapshots.HumanApproval.class);
		return snapshot == null ? null : snapshot.value();
	}

	@Override
	public List<HumanApproval> listByTask(String taskId) {
		return store.allBySecondary(TYPE, "task:" + taskId,
			PersistenceSnapshots.HumanApproval.class).stream()
			.map(PersistenceSnapshots.HumanApproval::value).toList();
	}

	@Override
	public List<HumanApproval> list() {
		return store.all(TYPE, PersistenceSnapshots.HumanApproval.class).stream()
			.map(PersistenceSnapshots.HumanApproval::value).toList();
	}
}
