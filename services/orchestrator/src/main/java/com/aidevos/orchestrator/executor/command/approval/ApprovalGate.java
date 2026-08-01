package com.aidevos.orchestrator.executor.command.approval;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ApprovalGate {

	private final Set<ApprovalRequest> approvedRequests = ConcurrentHashMap.newKeySet();

	public ApprovalDecision evaluate(ApprovalRequest request) {
		return approvedRequests.contains(request)
				? ApprovalDecision.APPROVED
				: ApprovalDecision.NOT_APPROVED;
	}

	public void approve(ApprovalRequest request) {
		approvedRequests.add(request);
	}
}
