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
		TaskDefinition task = new TaskDefinition();
		task.setId(planRun.getId() + ":" + step.id() + ":" + attempt.getNumber());
		task.setName(step.name());
		task.setDescription(step.description());
		task.setAgentName(step.assignment().agentName());
		task.setRequiredCapabilities(step.assignment().requiredCapabilities());
		Map<String, Object> parameters = new LinkedHashMap<>();
		if (step.toolName() != null && !step.toolName().isBlank()) {
			parameters.put("tool", Map.of("provider", step.toolProviderId(),
				"name", step.toolName(), "arguments", step.toolArguments()));
		}
		else {
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
