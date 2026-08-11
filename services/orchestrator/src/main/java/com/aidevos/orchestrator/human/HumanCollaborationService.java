package com.aidevos.orchestrator.human;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Human-in-the-loop collaboration: agents request human approval before a
 * gate node, the runtime session pauses, a human approves or rejects, and an
 * approved request resumes the session so the graph continues. Human
 * feedback is stored, audited and delivered to the agent team as a
 * HUMAN_RESPONSE message. Reuses the existing AgentRuntimeService (pause /
 * resume), AgentCollaborationService (team messages), AuditService and the
 * in-memory human repositories; no database migration is introduced.
 */
@Service
public class HumanCollaborationService {

	private final HumanApprovalRepository approvalRepository;
	private final HumanFeedbackRepository feedbackRepository;
	private final AuditService auditService;
	private final AgentRuntimeService runtimeService;
	private final AgentCollaborationService collaborationService;

	@Autowired
	public HumanCollaborationService(HumanApprovalRepository approvalRepository,
			HumanFeedbackRepository feedbackRepository, AuditService auditService,
			AgentRuntimeService runtimeService, AgentCollaborationService collaborationService) {
		this.approvalRepository = approvalRepository;
		this.feedbackRepository = feedbackRepository;
		this.auditService = auditService;
		this.runtimeService = runtimeService;
		this.collaborationService = collaborationService;
	}

	/**
	 * Creates a PENDING approval for the runtime session waiting at the gate
	 * node. The execution graph executor pauses the session around this call;
	 * an approved request resumes it.
	 */
	public HumanApproval requestApproval(String taskId, String sessionId, String teamId,
			String nodeId, String requester) {
		HumanApproval approval = new HumanApproval("approval-" + UUID.randomUUID(), taskId,
			sessionId, teamId, nodeId, HumanApprovalStatus.PENDING, requester, null, null,
			Instant.now(), null);
		approvalRepository.save(approval);
		auditService.humanEvent(EventType.HUMAN_APPROVAL_CREATED, approval.getApprovalId(),
			taskId, sessionId, requester, "CREATED", HumanApprovalStatus.PENDING.name(),
			"Human approval required at node " + value(nodeId), approvalMetadata(approval));
		return approval;
	}

	/**
	 * Approves a PENDING request, delivers the decision to the team and
	 * resumes the runtime session (which continues from the gate node).
	 */
	public HumanApproval approve(String approvalId, String reviewer, String comment) {
		HumanApproval approval = require(approvalId);
		requirePending(approval);
		approval.approve(reviewer, comment);
		approvalRepository.save(approval);
		auditService.humanEvent(EventType.HUMAN_APPROVED, approval.getApprovalId(),
			approval.getTaskId(), approval.getSessionId(), approval.getRequester(),
			HumanApprovalStatus.PENDING.name(), HumanApprovalStatus.APPROVED.name(),
			"Human approval granted by " + value(reviewer), approvalMetadata(approval));
		sendHumanResponse(approval, blank(comment) ? "Approved" : comment);
		resumeSession(approval);
		return approval;
	}

	/**
	 * Rejects a PENDING request. The decision is delivered to the team; the
	 * runtime session stays PAUSED until a decision changes or the session is
	 * stopped.
	 */
	public HumanApproval reject(String approvalId, String reviewer, String comment) {
		HumanApproval approval = require(approvalId);
		requirePending(approval);
		approval.reject(reviewer, comment);
		approvalRepository.save(approval);
		auditService.humanEvent(EventType.HUMAN_REJECTED, approval.getApprovalId(),
			approval.getTaskId(), approval.getSessionId(), approval.getRequester(),
			HumanApprovalStatus.PENDING.name(), HumanApprovalStatus.REJECTED.name(),
			"Human approval rejected by " + value(reviewer), approvalMetadata(approval));
		sendHumanResponse(approval, blank(comment) ? "Rejected" : comment);
		return approval;
	}

	/**
	 * Records human feedback for an agent and delivers it to the task's
	 * collaboration team as a HUMAN_RESPONSE message.
	 */
	public HumanFeedback addFeedback(String taskId, String sessionId, String agentType,
			String content) {
		HumanFeedback feedback = new HumanFeedback("feedback-" + UUID.randomUUID(), taskId,
			sessionId, agentType, content, Instant.now());
		feedbackRepository.save(feedback);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("feedbackId", feedback.getFeedbackId());
		if (agentType != null) {
			metadata.put("agentType", agentType);
		}
		auditService.humanEvent(EventType.HUMAN_FEEDBACK_ADDED, feedback.getFeedbackId(),
			taskId, sessionId, agentType, null, null, "Human feedback added",
			Map.copyOf(metadata));
		if (collaborationService != null && taskId != null) {
			collaborationService.teamForTask(taskId).ifPresent(team ->
				collaborationService.sendMessage(team.getTeamId(), "HUMAN", agentType,
					AgentMessageType.HUMAN_RESPONSE, content));
		}
		return feedback;
	}

	public Optional<HumanApproval> getApproval(String approvalId) {
		if (approvalId == null || approvalId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(approvalRepository.get(approvalId));
	}

	public List<HumanApproval> getTaskApprovals(String taskId) {
		return approvalRepository.listByTask(taskId);
	}

	public List<HumanFeedback> getFeedbacks(String taskId) {
		return feedbackRepository.listByTask(taskId);
	}

	public Optional<HumanFeedback> getFeedback(String feedbackId) {
		if (feedbackId == null || feedbackId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(feedbackRepository.get(feedbackId));
	}

	private void resumeSession(HumanApproval approval) {
		if (approval.getSessionId() == null || approval.getSessionId().isBlank()
			|| runtimeService == null) {
			return;
		}
		runtimeService.resumeSession(approval.getSessionId());
		auditService.humanEvent(EventType.HUMAN_RESUMED, approval.getApprovalId(),
			approval.getTaskId(), approval.getSessionId(), approval.getRequester(),
			"PAUSED", "RUNNING", "Runtime session resumed after human approval",
			approvalMetadata(approval));
	}

	private void sendHumanResponse(HumanApproval approval, String content) {
		if (approval.getTeamId() == null || collaborationService == null) {
			return;
		}
		collaborationService.sendMessage(approval.getTeamId(), "HUMAN",
			approval.getRequester(), AgentMessageType.HUMAN_RESPONSE, content);
	}

	private Map<String, Object> approvalMetadata(HumanApproval approval) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("approvalId", value(approval.getApprovalId()));
		if (approval.getNodeId() != null) {
			metadata.put("nodeId", approval.getNodeId());
		}
		if (approval.getTeamId() != null) {
			metadata.put("teamId", approval.getTeamId());
		}
		return metadata;
	}

	private HumanApproval require(String approvalId) {
		if (approvalId == null || approvalId.isBlank()) {
			throw new IllegalArgumentException("Approval id is required");
		}
		HumanApproval approval = approvalRepository.get(approvalId);
		if (approval == null) {
			throw new IllegalArgumentException("Approval not found: " + approvalId);
		}
		return approval;
	}

	private void requirePending(HumanApproval approval) {
		if (approval.getStatus() != HumanApprovalStatus.PENDING) {
			throw new IllegalStateException("Approval already reviewed: "
				+ approval.getApprovalId());
		}
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
