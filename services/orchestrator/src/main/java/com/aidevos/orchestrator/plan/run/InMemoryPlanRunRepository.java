package com.aidevos.orchestrator.plan.run;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryPlanRunRepository implements PlanRunRepository {
	private final Map<String, PlanRun> runs = new ConcurrentHashMap<>();
	private final Map<String, String> byApproval = new ConcurrentHashMap<>();
	public void create(String approvalId, PlanRun run) {
		if (byApproval.putIfAbsent(approvalId, run.getId()) != null)
			throw new IllegalStateException("Plan approval has already started a run");
		runs.put(run.getId(), run);
	}
	public void save(PlanRun run) { runs.put(run.getId(), run); }
	public PlanRun get(String id) { return runs.get(id); }
	public List<PlanRun> getAll() { return List.copyOf(runs.values()); }
	public String findRunIdByApproval(String id) { return byApproval.get(id); }
	public void remove(String approvalId, String runId) {
		runs.remove(runId); byApproval.remove(approvalId, runId);
	}
}
