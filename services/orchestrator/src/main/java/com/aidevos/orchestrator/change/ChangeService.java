package com.aidevos.orchestrator.change;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.feedback.PrFeedbackService;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Change management for AI code modifications. After an agent modifies code in
 * a workspace, a ChangeSet snapshots the git diff and change statistics and
 * moves through CREATED -> REVIEWING -> APPROVED | REJECTED. Read-only git
 * inspection only: this service never commits, pushes or merges.
 */
@Service
public class ChangeService {

	private static final String SYSTEM_REVIEWER = "SYSTEM";

	private final ChangeRepository repository;
	private final WorkspaceService workspaceService;
	private final AuditService auditService;
	private volatile PrFeedbackService feedbackService;
	private volatile QualityGateService qualityGateService;

	public ChangeService(ChangeRepository repository, WorkspaceService workspaceService,
			AuditService auditService) {
		this.repository = repository;
		this.workspaceService = workspaceService;
		this.auditService = auditService;
	}

	@Autowired(required = false)
	@Lazy
	public void setFeedbackService(PrFeedbackService feedbackService) {
		this.feedbackService = feedbackService;
	}

	@Autowired(required = false) @Lazy
	public void setQualityGateService(QualityGateService service) { this.qualityGateService = service; }

	/**
	 * Snapshots the current working-tree diff of the workspace as a new change
	 * set for the given task/execution. Never modifies the repository.
	 */
	public ChangeSet createChange(String taskId, String workspaceId, String projectId,
			String executionId) {
		if (qualityGateService != null) qualityGateService.assertAllowed(taskId);
		Workspace workspace = workspaceService.getWorkspace(workspaceId)
			.orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
		GitStatus gitStatus = workspaceService.checkGitStatus(workspaceId);
		GitDiff gitDiff = workspaceService.getGitDiff(workspaceId);
		String diffContent = workspaceService.getGitDiffContent(workspaceId);
		Instant now = Instant.now();
		ChangeSet changeSet = new ChangeSet("change-" + UUID.randomUUID(), taskId, workspaceId,
			projectId == null || projectId.isBlank() ? workspace.getProjectId() : projectId,
			executionId, gitStatus.getBranch(), diffContent, gitDiff.getStat(),
			gitDiff.getFilesChanged(), gitDiff.getInsertions(), gitDiff.getDeletions(),
			gitStatus.getModified(), gitStatus.getAdded(), gitStatus.getDeleted(), now);
		repository.save(changeSet);
		auditService.changeEvent(EventType.CHANGE_CREATED, taskId, changeSet.getChangeId(),
			null, ChangeStatus.CREATED.name(), "Change set created",
			Map.of("workspaceId", nullToEmpty(workspaceId),
				"executionId", nullToEmpty(executionId),
				"filesChanged", gitDiff.getFilesChanged()));
		return changeSet;
	}

	public Optional<ChangeSet> getChange(String changeId) {
		if (changeId == null || changeId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(changeId));
	}

	public List<ChangeSet> getChangesByTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return List.of();
		}
		List<ChangeSet> result = new ArrayList<>(repository.getByTaskId(taskId));
		result.sort(Comparator.comparing(ChangeSet::getCreatedAt).reversed());
		return result;
	}

	/** All change sets, newest first (read-only, for metrics/observability). */
	public List<ChangeSet> listChanges() {
		List<ChangeSet> result = new ArrayList<>(repository.list());
		result.sort(Comparator.comparing(ChangeSet::getCreatedAt).reversed());
		return result;
	}

	public String getDiff(String changeId) {
		return requireChange(changeId).getDiff();
	}

	public ChangeSet startReview(String changeId) {
		ChangeSet changeSet = requireChange(changeId);
		String from = changeSet.getStatus().name();
		changeSet.markReviewing();
		auditService.changeEvent(EventType.CHANGE_REVIEWING, changeSet.getTaskId(),
			changeSet.getChangeId(), from, ChangeStatus.REVIEWING.name(),
			"Change review started", Map.of());
		return changeSet;
	}

	public ChangeSet approve(String changeId, String reviewer) {
		ChangeSet changeSet = requireChange(changeId);
		String from = changeSet.getStatus().name();
		changeSet.markApproved(reviewer(reviewer));
		auditService.changeEvent(EventType.CHANGE_APPROVED, changeSet.getTaskId(),
			changeSet.getChangeId(), from, ChangeStatus.APPROVED.name(),
			"Change approved", Map.of("reviewedBy", reviewer(reviewer)));
		if (feedbackService != null) {
			feedbackService.onChangeApproved(changeId, changeSet.getTaskId());
		}
		return changeSet;
	}

	public ChangeSet reject(String changeId, String reviewer) {
		ChangeSet changeSet = requireChange(changeId);
		String from = changeSet.getStatus().name();
		changeSet.markRejected(reviewer(reviewer));
		auditService.changeEvent(EventType.CHANGE_REJECTED, changeSet.getTaskId(),
			changeSet.getChangeId(), from, ChangeStatus.REJECTED.name(),
			"Change rejected", Map.of("reviewedBy", reviewer(reviewer)));
		return changeSet;
	}

	public ChangeSet markCommitted(String changeId) {
		ChangeSet changeSet = requireChange(changeId);
		String from = changeSet.getStatus().name();
		changeSet.markCommitted();
		auditService.changeEvent(EventType.CHANGE_COMMITTED, changeSet.getTaskId(),
			changeSet.getChangeId(), from, ChangeStatus.COMMITTED.name(),
			"Change committed", Map.of());
		return changeSet;
	}

	private ChangeSet requireChange(String changeId) {
		return getChange(changeId)
			.orElseThrow(() -> new ResourceNotFoundException("Change", changeId));
	}

	private String reviewer(String reviewer) {
		return reviewer == null || reviewer.isBlank() ? SYSTEM_REVIEWER : reviewer;
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
