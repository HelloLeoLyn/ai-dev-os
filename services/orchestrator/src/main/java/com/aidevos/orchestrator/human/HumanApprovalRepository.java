package com.aidevos.orchestrator.human;

import java.util.List;

/**
 * Persistence contract for human approval requests. Implemented by the
 * in-memory store; no database migration is introduced in this phase.
 */
public interface HumanApprovalRepository {

	void save(HumanApproval approval);

	HumanApproval get(String approvalId);

	List<HumanApproval> listByTask(String taskId);

	List<HumanApproval> list();
}
