package com.aidevos.orchestrator.execution.query;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.model.ExecutionRecord;
import org.springframework.stereotype.Service;

@Service
public class ExecutionRecordQueryService {

	private final ExecutionRecordManager executionRecordManager;

	public ExecutionRecordQueryService(ExecutionRecordManager executionRecordManager) {
		this.executionRecordManager = executionRecordManager;
	}

	public List<ExecutionRecordSummary> getAll(String status, String taskId) {
		return executionRecordManager.getAll().stream()
			.filter(record -> matchesStatus(record, status))
			.filter(record -> matchesTask(record, taskId))
			.map(this::summary)
			.toList();
	}

	public Optional<ExecutionRecordDetail> get(String id) {
		return Optional.ofNullable(executionRecordManager.get(id)).map(this::detail);
	}

	private boolean matchesStatus(ExecutionRecord record, String status) {
		return status == null || (record.getStatus() != null
			&& record.getStatus().equalsIgnoreCase(status));
	}

	private boolean matchesTask(ExecutionRecord record, String taskId) {
		return taskId == null || taskId.equals(record.getTaskId());
	}

	private ExecutionRecordSummary summary(ExecutionRecord record) {
		return new ExecutionRecordSummary(record.getId(), record.getTaskId(),
			record.getAgentName(), record.getStatus(), record.getMessage());
	}

	private ExecutionRecordDetail detail(ExecutionRecord record) {
			return new ExecutionRecordDetail(record.getId(), record.getTaskId(),
			record.getAgentName(), record.getExecutorName(), record.getStatus(), record.getMessage(),
			record.getOutput(), record.getReport(), record.getArtifacts(), record.getExecutionId(),
			record.getJobId(), record.getPlanRunId(), record.getStepRunId(), record.getAttemptId(),
			record.getWorkspace(), record.getSandbox(), record.getApprovalId(),
			record.getBranch(), record.getBeforeHead(), record.getAfterHead(), record.getExitCode(),
			record.getCodexThreadId(), record.getStartedAt(), record.getCompletedAt());
	}
}
