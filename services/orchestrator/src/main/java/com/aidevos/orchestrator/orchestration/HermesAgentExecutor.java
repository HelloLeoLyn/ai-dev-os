package com.aidevos.orchestrator.orchestration;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Component;

/**
 * HERMES node: understands the request and produces the execution plan.
 * Reuses the existing PlannerService (same planning path as the legacy
 * coordinator). A planning result already computed by executeGraph is reused
 * so the planner is not invoked twice.
 */
@Component
public class HermesAgentExecutor implements AgentExecutor {

	private static final String HERMES_PLANNER = "hermes";

	private final PlannerService plannerService;

	public HermesAgentExecutor(PlannerService plannerService) {
		this.plannerService = plannerService;
	}

	@Override
	public AgentType type() {
		return AgentType.HERMES;
	}

	@Override
	public AgentExecutionResult execute(AgentExecutionContext context) {
		TaskRecord task = context.getTask();
		if (task == null) {
			return failure(context, "Task is required for planning");
		}
		if (context.getPlanningResult() != null && context.getPlanningResult().success()
			&& context.getPlanningResult().plan() != null) {
			return success(context, "Plan created: " + context.getPlanningResult().plan().id());
		}
		try {
			String goal = goal(task, context);
			PlanningResult result = plannerService.createPlan(new PlanningRequest(
				task.getTaskId(), goal, HERMES_PLANNER, null, null, null, null, null));
			if (!result.success() || result.plan() == null) {
				return failure(context, "Planning failed: " + joinErrors(result.errors()));
			}
			return success(context, "Plan created: " + result.plan().id());
		}
		catch (RuntimeException exception) {
			return failure(context, errorMessage(exception));
		}
	}

	/**
	 * Builds the planning goal from the task, appending the memory context
	 * (warnings and recommended solutions) so historical experience is
	 * carried into the Hermes plan.
	 */
	private String goal(TaskRecord task, AgentExecutionContext context) {
		StringBuilder goal = new StringBuilder(
			task.getDescription() == null || task.getDescription().isBlank()
				? task.getName() : task.getDescription());
		if (context.getMemoryHints() == null) {
			return goal.toString();
		}
		com.aidevos.orchestrator.memory.MemoryContext hints = context.getMemoryHints();
		if (!hints.getWarnings().isEmpty()) {
			goal.append(System.lineSeparator()).append("历史已知问题:");
			for (String warning : hints.getWarnings()) {
				goal.append(System.lineSeparator()).append("- ").append(warning);
			}
		}
		if (!hints.getRecommendations().isEmpty()) {
			goal.append(System.lineSeparator()).append("历史推荐方案:");
			for (String recommendation : hints.getRecommendations()) {
				goal.append(System.lineSeparator()).append("- ").append(recommendation);
			}
		}
		return goal.toString();
	}

	private String joinErrors(java.util.List<String> errors) {
		return errors == null || errors.isEmpty() ? "unknown" : String.join(", ", errors);
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
