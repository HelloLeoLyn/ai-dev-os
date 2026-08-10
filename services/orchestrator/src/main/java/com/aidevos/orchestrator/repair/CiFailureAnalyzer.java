package com.aidevos.orchestrator.repair;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.ci.CiReport;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import org.springframework.stereotype.Service;

/**
 * Maps a failed CI run (CiRunRecord + provider CiReport) into a
 * FailureContext of source type CI_FAILURE. The workspace git state is
 * snapshot so the repair loop knows what the failing commit changed. This
 * class only builds the failure context; the repair loop itself lives in
 * RepairCoordinator.
 */
@Service
public class CiFailureAnalyzer {

	public static final String SOURCE_TYPE_CI_FAILURE = "CI_FAILURE";

	private final WorkspaceService workspaceService;
	private final ChangeService changeService;

	public CiFailureAnalyzer(WorkspaceService workspaceService, ChangeService changeService) {
		this.workspaceService = workspaceService;
		this.changeService = changeService;
	}

	/**
	 * Builds the failure context for a CI run that ended FAILED. The report
	 * url is carried as the test report, the workspace git diff snapshot as
	 * gitDiff, and commit/branch/changedFiles come from the run and the
	 * workspace state.
	 */
	public FailureContext analyze(CiRunRecord run, String workspaceId, CiReport report) {
		if (run == null) {
			throw new IllegalArgumentException("CiRunRecord is required");
		}
		String diffStat = workingTreeDiffStat(workspaceId);
		ChangeSet change = latestChange(run.getTaskId());
		String gitDiff = diffStat.isBlank() && change != null
			? value(change.getDiffStat()) : diffStat;
		int changedFiles = change == null ? 0 : change.getFilesChanged();
		return new FailureContext(
			value(run.getTaskId()),
			value(workspaceId),
			null,
			"CI run failed: " + value(run.getPipelineId()),
			null,
			report == null ? value(run.getReportUrl())
				: fallback(report.reportUrl(), report.summary()),
			gitDiff,
			SOURCE_TYPE_CI_FAILURE,
			value(run.getCiRunId()),
			value(run.getCommitHash()),
			value(run.getBranch()),
			changedFiles,
			Instant.now());
	}

	/**
	 * The change under test: after a commit and push the working tree is clean,
	 * so the files changed by the failing CI run are taken from the task's
	 * newest ChangeSet snapshot.
	 */
	private ChangeSet latestChange(String taskId) {
		if (changeService == null || taskId == null || taskId.isBlank()) {
			return null;
		}
		try {
			List<ChangeSet> changes = changeService.getChangesByTask(taskId);
			return changes == null || changes.isEmpty() ? null : changes.get(0);
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private String workingTreeDiffStat(String workspaceId) {
		GitDiff diff = gitDiff(workspaceId);
		return diff == null ? "" : value(diff.getStat());
	}

	private GitDiff gitDiff(String workspaceId) {
		if (workspaceId == null || workspaceId.isBlank()) {
			return null;
		}
		try {
			return workspaceService.getGitDiff(workspaceId);
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	private String fallback(String primary, String secondary) {
		String first = value(primary);
		return first.isBlank() ? value(secondary) : first;
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
