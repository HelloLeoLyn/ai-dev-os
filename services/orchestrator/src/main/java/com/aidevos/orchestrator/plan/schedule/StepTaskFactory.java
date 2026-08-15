package com.aidevos.orchestrator.plan.schedule;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import org.springframework.stereotype.Component;

@Component
public class StepTaskFactory {

	public TaskDefinition create(PlanRun planRun, PlanStep step, StepRun stepRun,
			StepAttempt attempt) {
		return create(planRun, step, stepRun, attempt, Map.of());
	}

	public TaskDefinition create(PlanRun planRun, PlanStep step, StepRun stepRun,
			StepAttempt attempt, Map<String, Object> resolvedInputs) {
		TaskDefinition task = new TaskDefinition();
		task.setId(planRun.getId() + ":" + step.id() + ":" + attempt.getNumber());
		task.setName(step.name());
		task.setDescription(step.description());
		task.setAgentName(step.assignment().agentName());
		task.setRequiredCapabilities(step.assignment().requiredCapabilities());
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.putAll(step.parameters());
		if (resolvedInputs != null && !resolvedInputs.isEmpty()) {
			parameters.put("inputs", Map.copyOf(resolvedInputs));
		}
		if (step.toolName() != null && !step.toolName().isBlank()) {
			parameters.put("tool", Map.of("provider", step.toolProviderId(),
				"name", step.toolName(), "arguments", step.toolArguments()));
		}
		else if (step.parameters().isEmpty()) {
			// Preserve the Phase 5 constructor contract where toolArguments carried
			// ordinary step parameters for non-tool steps.
			parameters.putAll(step.toolArguments());
		}
		task.setParameters(parameters);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("originalTaskId", value(planRun.getOriginalTaskId()));
		metadata.put("projectId", snapshotValue(planRun, "projectId"));
		metadata.put("workspaceId", snapshotValue(planRun, "workspaceId"));
		metadata.put("workspacePath", snapshotValue(planRun, "workspacePath"));
		metadata.put("executionMode", snapshotValue(planRun, "executionMode"));
		metadata.putAll(Map.of(
			"planRunId", planRun.getId(),
			"stepRunId", stepRun.getId(),
			"attemptId", attempt.getId(),
			"planId", planRun.getPlanId(),
			"planVersion", planRun.getPlanVersion(),
			"stepId", step.id()));
		task.setMetadata(Map.copyOf(metadata));
		parameters.put("originalTaskId", metadata.get("originalTaskId"));
		parameters.put("projectId", metadata.get("projectId"));
		parameters.put("workspaceId", metadata.get("workspaceId"));
		parameters.put("workspacePath", metadata.get("workspacePath"));
		parameters.put("executionMode", metadata.get("executionMode"));
		String taskType = snapshotValue(planRun, "taskType");
		if (!taskType.isBlank()) parameters.put("taskType", taskType);
		if ("READ_ONLY".equals(metadata.get("executionMode"))) {
			parameters.put("sandbox", "read-only");
		}
		task.setParameters(parameters);
		task.setStatus("PLANNED");
		return task;
	}

	private String snapshotValue(PlanRun run, String key) {
		if (run.getPlan().snapshot() == null) return "";
		Object value = run.getPlan().snapshot().plannerMetadata().get(key);
		return value == null ? "" : String.valueOf(value);
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
