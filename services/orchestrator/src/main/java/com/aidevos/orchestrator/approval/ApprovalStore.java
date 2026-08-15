package com.aidevos.orchestrator.approval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class ApprovalStore implements CodingApprovalRepository {

	private final Map<String, CodingApprovalRequest> requests = new LinkedHashMap<>();

	public synchronized void save(CodingApprovalRequest request) {
		requests.put(request.getId(), request);
	}

	public synchronized CodingApprovalRequest get(String id) {
		return requests.get(id);
	}

	public synchronized List<CodingApprovalRequest> getAll() {
		return new ArrayList<>(requests.values());
	}

	public synchronized CodingApprovalRequest findReusable(String taskId, String jobId, String authority, String operation) {
		return requests.values().stream()
			.filter(request -> sameScope(request, taskId, jobId))
			.filter(request -> authority.equals(request.getAuthority()) && operation.equals(request.getOperation()))
			.filter(request -> request.getStatus() == ApprovalStatus.PENDING
				|| request.getStatus() == ApprovalStatus.APPROVED)
			.reduce((first, second) -> second)
			.orElse(null);
	}

	private boolean sameScope(CodingApprovalRequest request, String taskId, String jobId) {
		if (jobId != null) {
			return jobId.equals(request.getJobId());
		}
		return request.getJobId() == null && taskId != null && taskId.equals(request.getTaskId());
	}
}
