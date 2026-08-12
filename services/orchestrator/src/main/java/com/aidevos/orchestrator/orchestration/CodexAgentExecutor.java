package com.aidevos.orchestrator.orchestration;

import java.util.UUID;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

/**
 * CODEX node: modifies the workspace code through the existing executor
 * pipeline (ExecutorManager -> CodexExecutor), saves an ExecutionRecord and
 * snapshots a ChangeSet. Thin adapter; the codex business logic stays in
 * CodexExecutor.
 */
@Component
public class CodexAgentExecutor implements AgentExecutor {

	private static final String DEFAULT_AGENT = "coder";

	private final ExecutorManager executorManager;
	private final WorkspaceService workspaceService;
	private final ExecutionRecordManager executionRecordManager;
	private final ChangeService changeService;
	private final AuditService auditService;

	public CodexAgentExecutor(ExecutorManager executorManager,
			WorkspaceService workspaceService, ExecutionRecordManager executionRecordManager,
			ChangeService changeService, AuditService auditService) {
		this.executorManager = executorManager;
		this.workspaceService = workspaceService;
		this.executionRecordManager = executionRecordManager;
		this.changeService = changeService;
		this.auditService = auditService;
	}

	@Override
	public AgentType type() {
		return AgentType.CODEX;
	}

	@Override
	public AgentExecutionResult execute(AgentExecutionContext context) {
		TaskRecord task = context.getTask();
		if (task == null) {
			return failure(context, "Task is required for coding");
		}
		String workspaceId = context.getWorkspaceId();
		String executionId = "graph-" + context.getGraphId() + ":" + context.getNodeId();
		try {
			ExecutionResult result = executeAgent(DEFAULT_AGENT, task, context, executionId);
			if (result == null || !result.isSuccess()) {
				return failure(context, message(result));
			}
			recordChange(task, workspaceId, executionId);
			return success(context, summarize(result));
		}
		catch (RuntimeException exception) {
			return failure(context, errorMessage(exception));
		}
	}

	private ExecutionResult executeAgent(String agentName, TaskRecord task,
			AgentExecutionContext context, String executionId) {
		com.aidevos.orchestrator.executor.AgentExecutor executor = executorManager.getExecutor(agentName);
		if (executor == null) {
			throw new IllegalStateException("Executor not found for agent: " + agentName);
		}
		String workspacePath = context.getWorkspacePath();
		ExecutionContext executionContext = new ExecutionContext();
		executionContext.setExecutionId(executionId);
		executionContext.setTaskId(task.getTaskId());
		executionContext.setTaskName(task.getName());
		executionContext.setProjectId(task.getProjectId());
		executionContext.getMetadata().put("workspaceId", context.getWorkspaceId());
		executionContext.getMetadata().put("executionMode", task.getExecutionMode().name());
		executionContext.getParameters().put("executionMode", task.getExecutionMode().name());
		if (task.getExecutionMode() == com.aidevos.orchestrator.taskcenter.ExecutionMode.READ_ONLY) {
			executionContext.getParameters().put("sandbox", "read-only");
		}
		executionContext.setDescription(task.getDescription());
		executionContext.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		executionContext.setAgentName(agentName);
		if (workspacePath != null) {
			executionContext.setWorkspace(workspacePath);
			executionContext.getParameters().put("workspace", workspacePath);
		}
		ExecutionResult result = executor.execute(executionContext);
		saveExecutionRecord(task, agentName, executionId, result, context.getWorkspaceId(), workspacePath);
		return result;
	}

	private void saveExecutionRecord(TaskRecord task, String agentName, String executionId,
			ExecutionResult result, String workspaceId, String workspacePath) {
		try {
			ExecutionRecord record = new ExecutionRecord();
			record.setId(executionId);
			record.setExecutionId(executionId);
			record.setTaskId(task.getTaskId());
			record.setAgentName(agentName);
			record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
			record.setMessage(result.getMessage());
			record.setOutput(result.getOutput());
			record.setWorkspace(workspacePath);
			java.time.Instant now = java.time.Instant.now();
			record.setStartedAt(now);
			record.setCompletedAt(now);
			executionRecordManager.save(record);
		}
		catch (RuntimeException exception) {
			// Execution record persistence must not break the agent flow.
		}
	}

	private void recordChange(TaskRecord task, String workspaceId, String executionId) {
		if (changeService == null || workspaceId == null || workspaceId.isBlank()
				|| task.getExecutionMode() == com.aidevos.orchestrator.taskcenter.ExecutionMode.READ_ONLY) {
			return;
		}
		try {
			changeService.createChange(task.getTaskId(), workspaceId, task.getProjectId(),
				executionId);
		}
		catch (RuntimeException exception) {
			// Change tracking must not break the agent flow.
		}
	}

	private String summarize(ExecutionResult result) {
		String output = result.getOutput();
		if (output != null && !output.isBlank()) {
			return output;
		}
		return result.getMessage() == null || result.getMessage().isBlank()
			? "Codex execution succeeded" : result.getMessage();
	}

	private String message(ExecutionResult result) {
		return result == null || result.getMessage() == null || result.getMessage().isBlank()
			? "Codex execution failed" : result.getMessage();
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private AgentExecutionResult success(AgentExecutionContext context, String output) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED, output, null);
	}

	private AgentExecutionResult failure(AgentExecutionContext context, String error) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null, error);
	}
}
