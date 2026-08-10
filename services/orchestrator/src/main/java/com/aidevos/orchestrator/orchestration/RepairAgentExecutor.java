package com.aidevos.orchestrator.orchestration;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import org.springframework.stereotype.Component;

/**
 * REPAIR_AGENT node: analyses the failure (the input carries the reproduced
 * failure from the TEST_AGENT_ANALYZE node) and produces a repair plan.
 * Reuses RepairCoordinator's failure analysis (Hermes planning), never
 * duplicating the repair loop.
 */
@Component
public class RepairAgentExecutor implements AgentExecutor {

	private final RepairCoordinator repairCoordinator;

	public RepairAgentExecutor(RepairCoordinator repairCoordinator) {
		this.repairCoordinator = repairCoordinator;
	}

	@Override
	public AgentType type() {
		return AgentType.REPAIR_AGENT;
	}

	@Override
	public AgentExecutionResult execute(AgentExecutionContext context) {
		String taskId = context.getTaskId();
		if (taskId == null || taskId.isBlank()) {
			return failure(context, "Task is required for repair analysis");
		}
		try {
			PlanningResult result = repairCoordinator.analyzeFailure(taskId,
				value(context.getInput()));
			if (!result.success() || result.plan() == null) {
				return failure(context, "Repair analysis failed: " + joinErrors(result.errors()));
			}
			return success(context, "Repair plan: " + result.plan().id());
		}
		catch (RuntimeException exception) {
			return failure(context, errorMessage(exception));
		}
	}

	private String joinErrors(java.util.List<String> errors) {
		return errors == null || errors.isEmpty() ? "unknown" : String.join(", ", errors);
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private AgentExecutionResult success(AgentExecutionContext context, String output) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED, output, null);
	}

	private AgentExecutionResult failure(AgentExecutionContext context, String error) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null, error);
	}
}
