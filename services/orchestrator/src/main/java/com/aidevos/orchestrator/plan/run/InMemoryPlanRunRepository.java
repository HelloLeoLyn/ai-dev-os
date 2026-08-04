package com.aidevos.orchestrator.plan.run;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryPlanRunRepository implements PlanRunRepository {
	private final Map<String, PlanRun> runs = new ConcurrentHashMap<>();
	private final Map<String, String> byApproval = new ConcurrentHashMap<>();
	public void create(String approvalId, PlanRun run) {
		if (findRunIdByApproval(approvalId) != null) {
			throw new IllegalStateException("Plan approval has already started a run");
		}
		createIfAbsent(approvalId, run);
	}
	public synchronized PlanRun createIfAbsent(String approvalId, PlanRun run) {
		String existingId = byApproval.putIfAbsent(approvalId, run.getId());
		if (existingId != null) {
			return runs.get(existingId);
		}
		runs.put(run.getId(), run);
		return run;
	}
	public void save(PlanRun run) { runs.put(run.getId(), run); }
	public synchronized boolean saveIfUnchanged(PlanRun run, int expectedVersion) {
		PlanRun stored = runs.get(run.getId());
		if (stored == null || stored.getVersion() != expectedVersion) {
			return false;
		}
		stored.bumpVersion();
		return true;
	}
	public PlanRun get(String id) { return runs.get(id); }
	public List<PlanRun> getAll() { return List.copyOf(runs.values()); }
	public String findRunIdByApproval(String id) { return byApproval.get(id); }
	public void remove(String approvalId, String runId) {
		runs.remove(runId); byApproval.remove(approvalId, runId);
	}
	public synchronized Optional<PlanRun> claimCoordinator(String runId, String owner,
			Instant now, Duration leaseDuration) {
		PlanRun stored = runs.get(runId);
		if (stored == null) {
			return Optional.empty();
		}
		if (stored.getCoordinatorOwner() != null && stored.getCoordinatorExpiresAt() != null
				&& stored.getCoordinatorExpiresAt().isAfter(now)
				&& !stored.getCoordinatorOwner().equals(owner)) {
			return Optional.empty();
		}
		long token = stored.getCoordinatorToken() + 1;
		stored.applyCoordinatorLease(owner, token, now.plus(leaseDuration));
		return saveIfUnchanged(stored, stored.getVersion())
			? Optional.of(stored) : Optional.empty();
	}
	public synchronized boolean releaseCoordinator(String runId, String owner, long token) {
		PlanRun stored = runs.get(runId);
		if (stored == null || !owner.equals(stored.getCoordinatorOwner())
				|| stored.getCoordinatorToken() != token) {
			return false;
		}
		stored.clearCoordinatorLease();
		stored.bumpVersion();
		return true;
	}
}
