package com.aidevos.orchestrator.plan.approval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class PlanApprovalStore implements PlanApprovalRepository {

	private final Map<String, PlanApprovalRequest> requests = new LinkedHashMap<>();
	private final Map<String, String> frozenVersions = new LinkedHashMap<>();

	public synchronized PlanApprovalRequest save(PlanApprovalRequest request) {
		String versionKey = versionKey(request.getPlanId(), request.getPlanVersion());
		String frozenHash = frozenVersions.get(versionKey);
		if (frozenHash != null && !frozenHash.equals(request.getPlanSnapshotHash())) {
			throw new IllegalStateException("Plan version content is frozen: " + versionKey);
		}
		PlanApprovalRequest existing = find(request.getPlanId(), request.getPlanVersion(),
			request.getRequestId(), request.getPlanSnapshotHash());
		if (existing != null) {
			return existing;
		}
		frozenVersions.put(versionKey, request.getPlanSnapshotHash());
		requests.put(request.getId(), request);
		return request;
	}

	public synchronized PlanApprovalRequest get(String id) {
		return requests.get(id);
	}

	public synchronized List<PlanApprovalRequest> getAll() {
		return new ArrayList<>(requests.values());
	}

	public synchronized PlanApprovalRequest find(String planId, int planVersion, String requestId,
			String hash) {
		return requests.values().stream()
			.filter(request -> request.getPlanId().equals(planId))
			.filter(request -> request.getPlanVersion() == planVersion)
			.filter(request -> request.getRequestId().equals(requestId))
			.filter(request -> request.getPlanSnapshotHash().equals(hash))
			.findFirst().orElse(null);
	}

	private String versionKey(String planId, int version) {
		return planId + ":" + version;
	}
}
