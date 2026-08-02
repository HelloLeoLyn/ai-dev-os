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
		task.setMetadata(Map.of(
			"planRunId", planRun.getId(),
			"stepRunId", stepRun.getId(),
			"attemptId", attempt.getId(),
			"planId", planRun.getPlanId(),
			"planVersion", planRun.getPlanVersion(),
			"stepId", step.id()));
		task.setStatus("PLANNED");
		return task;
	}
}
