package com.aidevos.orchestrator.planner.replan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class ReplanRequestStore implements ReplanRequestRepository {

	private final Map<String, ReplanRequest> requests = new LinkedHashMap<>();

	public synchronized void save(ReplanRequest request) { requests.put(request.id(), request); }
	public synchronized ReplanRequest get(String id) { return requests.get(id); }
	public synchronized List<ReplanRequest> getAll() {
		return new ArrayList<>(requests.values());
	}
	public synchronized ReplanRequest findByPlanRun(String planRunId) {
		return requests.values().stream()
			.filter(request -> request.failedPlanRunId().equals(planRunId))
			.findFirst().orElse(null);
	}
}
