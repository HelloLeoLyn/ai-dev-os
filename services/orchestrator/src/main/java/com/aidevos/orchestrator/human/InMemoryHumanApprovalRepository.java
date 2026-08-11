package com.aidevos.orchestrator.human;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory human approval store. Human collaboration is kept in-memory in
 * this phase (no database migration), so this repository is registered
 * regardless of the persistence type.
 */
@Repository
public class InMemoryHumanApprovalRepository implements HumanApprovalRepository {

	private final Map<String, HumanApproval> approvals = new LinkedHashMap<>();

	@Override
	public synchronized void save(HumanApproval approval) {
		approvals.put(approval.getApprovalId(), approval);
	}

	@Override
	public synchronized HumanApproval get(String approvalId) {
		return approvals.get(approvalId);
	}

	@Override
	public synchronized List<HumanApproval> listByTask(String taskId) {
		if (taskId == null) {
			return List.of();
		}
		return approvals.values().stream()
			.filter(approval -> taskId.equals(approval.getTaskId()))
			.toList();
	}

	@Override
	public synchronized List<HumanApproval> list() {
		return List.copyOf(approvals.values());
	}
}
