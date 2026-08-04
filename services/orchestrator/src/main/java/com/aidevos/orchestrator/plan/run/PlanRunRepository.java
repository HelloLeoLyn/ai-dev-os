package com.aidevos.orchestrator.plan.run;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlanRunRepository {
	void create(String approvalId, PlanRun run);
	PlanRun createIfAbsent(String approvalId, PlanRun run);
	void save(PlanRun run);
	boolean saveIfUnchanged(PlanRun run, int expectedVersion);
	PlanRun get(String runId);
	List<PlanRun> getAll();
	String findRunIdByApproval(String approvalId);
	void remove(String approvalId, String runId);
	Optional<PlanRun> claimCoordinator(String runId, String owner, Instant now,
			Duration leaseDuration);
	boolean releaseCoordinator(String runId, String owner, long token);
}
