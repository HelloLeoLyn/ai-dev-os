package com.aidevos.orchestrator.plan.run;

import java.util.List;

public interface PlanRunRepository {
	void create(String approvalId, PlanRun run);
	void save(PlanRun run);
	PlanRun get(String runId);
	List<PlanRun> getAll();
	String findRunIdByApproval(String approvalId);
	void remove(String approvalId, String runId);
}
