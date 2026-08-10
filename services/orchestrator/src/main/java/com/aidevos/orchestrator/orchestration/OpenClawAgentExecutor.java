package com.aidevos.orchestrator.orchestration;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Component;

/**
 * OPENCLAW node: drives the browser through the existing executor pipeline.
 * Thin adapter; the browser business logic stays in the registered executor.
 */
@Component
public class OpenClawAgentExecutor implements AgentExecutor {

	private static final String DEFAULT_AGENT = "browser-agent";

	private final ExecutorManager executorManager;
	private final ExecutionRecordManager executionRecordManager;

	public OpenClawAgentExecutor(ExecutorManager executorManager,
			ExecutionRecordManager executionRecordManager) {
		this.executorManager = executorManager;
		this.executionRecordManager = executionRecordManager;
	}

	@Override
	public AgentType type() {
		return AgentType.OPENCLAW;
	}

	@Override
	public AgentExecutionResult execute(AgentExecutionContext context) {
		TaskRecord task = context.getTask();
		if (task == null) {
			return failure(context, "Task is required for browser execution");
		}
		String executionId = "graph-" + context.getGraphId() + ":" + context.getNodeId();
		try {
			ExecutionResult result = executeAgent(DEFAULT_AGENT, task, context, executionId);
			if (result == null || !result.isSuccess()) {
				return failure(context, result == null || result.getMessage() == null
					|| result.getMessage().isBlank() ? "Browser execution failed"
						: result.getMessage());
			}
			String output = result.getOutput();
			return success(context, output == null || output.isBlank()
				? "Browser execution succeeded" : output);
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
		ExecutionContext executionContext = new ExecutionContext();
		executionContext.setExecutionId(executionId);
		executionContext.setTaskId(task.getTaskId());
		executionContext.setTaskName(task.getName());
		executionContext.setProjectId(task.getProjectId());
		executionContext.setDescription(task.getDescription());
		executionContext.setInput(task.getDescription() == null || task.getDescription().isBlank()
			? task.getName() : task.getDescription());
		executionContext.setAgentName(agentName);
		if (context.getWorkspacePath() != null) {
			executionContext.setWorkspace(context.getWorkspacePath());
		}
		ExecutionResult result = executor.execute(executionContext);
		saveExecutionRecord(task, agentName, executionId, result);
		return result;
	}

	private void saveExecutionRecord(TaskRecord task, String agentName, String executionId,
			ExecutionResult result) {
		try {
			ExecutionRecord record = new ExecutionRecord();
			record.setId(executionId);
			record.setExecutionId(executionId);
			record.setTaskId(task.getTaskId());
			record.setAgentName(agentName);
			record.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
			record.setMessage(result.getMessage());
			record.setOutput(result.getOutput());
			java.time.Instant now = java.time.Instant.now();
			record.setStartedAt(now);
			record.setCompletedAt(now);
			executionRecordManager.save(record);
		}
		catch (RuntimeException exception) {
			// Execution record persistence must not break the agent flow.
		}
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
