package com.aidevos.orchestrator.plan.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanRunContextPropagationTest {

	@Test
	void propagatesApprovedTaskContextAndForcesReadOnlySandbox() {
		PlanSnapshot snapshot = new PlanSnapshot(List.of(), Set.of(), List.of(), Set.of(), "v1",
			Map.of("projectId", "project-1", "workspaceId", "workspace-1",
				"workspacePath", "/workspace/project", "executionMode", "READ_ONLY"));
		PlanStep step = new PlanStep("step-1", "Analyze", "Read only", StepStatus.PLANNED,
			new AgentAssignment("planner", List.of("analysis"), List.of()), null, null,
			Map.of(), List.of(), null, null, false);
		Plan plan = new Plan("plan-1", 1, "Analyze", PlanStatus.DRAFT, List.of(step),
			List.of(), snapshot, Instant.now());
		PlanRun run = new PlanRun("run-1", "approval-1", "task-1", plan, List.of(),
			Instant.now());
		StepRun stepRun = new StepRun("step-run-1", "step-1");
		StepAttempt attempt = stepRun.startAttempt("attempt-1", Instant.now());

		TaskDefinition task = new StepTaskFactory().create(run, step, stepRun, attempt);

		assertEquals("task-1", task.getMetadata().get("originalTaskId"));
		assertEquals("project-1", task.getMetadata().get("projectId"));
		assertEquals("workspace-1", task.getMetadata().get("workspaceId"));
		assertEquals("/workspace/project", task.getMetadata().get("workspacePath"));
		assertEquals("READ_ONLY", task.getMetadata().get("executionMode"));
		assertEquals("read-only", task.getParameters().get("sandbox"));
	}
}
