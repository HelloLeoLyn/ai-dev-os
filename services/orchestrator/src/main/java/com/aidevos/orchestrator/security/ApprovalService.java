package com.aidevos.orchestrator.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Human approval workflow for dangerous permissions (terminal, docker,
 * network). An approved permission unlocks the corresponding tool for the
 * task until the sandbox is destroyed; approvals are never granted
 * automatically.
 */
@Service
public class ApprovalService {

	private final Map<String, ApprovalRequest> requests = new ConcurrentHashMap<>();
	private final AuditService auditService;

	public ApprovalService() {
		this(AuditService.noop());
	}

	@Autowired
	public ApprovalService(AuditService auditService) {
		this.auditService = auditService;
	}

	public ApprovalRequest request(String taskId, AgentType agentType,
			SecurityPermission permission, String reason) {
		ApprovalRequest approval = new ApprovalRequest("approval-" + UUID.randomUUID(),
			taskId, agentType, permission, reason);
		requests.put(approval.getRequestId(), approval);
		auditService.securityEvent(EventType.USER_OPERATION, taskId,
			agentType == null ? null : agentType.name(),
			permission == null ? null : permission.name(),
			ApprovalRequest.ApprovalStatus.PENDING.name(),
			"Approval requested for permission " + permission, Map.of(
				"requestId", approval.getRequestId(), "permission",
				permission == null ? "" : permission.name(),
				"reason", reason == null ? "" : reason));
		return approval;
	}

	public Optional<ApprovalRequest> approve(String requestId) {
		return decide(requestId, true);
	}

	public Optional<ApprovalRequest> reject(String requestId) {
		return decide(requestId, false);
	}

	public Optional<ApprovalRequest> get(String requestId) {
		return Optional.ofNullable(requests.get(requestId));
	}

	public List<ApprovalRequest> list() {
		return List.copyOf(requests.values());
	}

	/**
	 * Whether the task currently holds an approved, not-yet-revoked approval
	 * for the permission.
	 */
	public boolean isApproved(String taskId, SecurityPermission permission) {
		return requests.values().stream().anyMatch(request ->
			request.getTaskId().equals(taskId) && request.getPermission() == permission
				&& request.isApproved());
	}

	private Optional<ApprovalRequest> decide(String requestId, boolean approved) {
		ApprovalRequest request = requests.get(requestId);
		if (request == null) {
			return Optional.empty();
		}
		if (approved) {
			request.approve();
		}
		else {
			request.reject();
		}
		auditService.securityEvent(approved ? EventType.PERMISSION_GRANTED
			: EventType.PERMISSION_DENIED, request.getTaskId(),
			request.getAgentType() == null ? null : request.getAgentType().name(),
			request.getPermission().name(), request.getStatus().name(),
			"Approval " + (approved ? "approved" : "rejected") + ": " + requestId,
			Map.of("requestId", requestId));
		return Optional.of(request);
	}
}
