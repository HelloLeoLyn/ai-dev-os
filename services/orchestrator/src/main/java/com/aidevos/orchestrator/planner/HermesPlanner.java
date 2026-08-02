package com.aidevos.orchestrator.planner;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import org.springframework.stereotype.Component;

@Component
public class HermesPlanner implements Planner {

	public static final String NAME = "hermes";

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PlanDraft plan(PlanningRequest request) {
		PlanSnapshot.AgentSnapshot agent = request.snapshot() == null ? null
			: request.snapshot().agents().stream().filter(PlanSnapshot.AgentSnapshot::enabled)
				.findFirst().orElse(null);
		AgentAssignment assignment = agent == null
			? new AgentAssignment(null, List.of(), List.of())
			: new AgentAssignment(agent.name(), agent.capabilities(), List.of());
		PlanStep step = new PlanStep("step-1", "Handle request", request.goal(),
			StepStatus.PLANNED, assignment, null, null, Map.of(),
			List.of(new ExpectedArtifact("result", "result", "application/json", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		return new PlanDraft("plan-" + request.requestId(), 1, request.goal(), List.of(step),
			List.of(), request.snapshot(), name(), request.model(), request.promptVersion(),
			request.metadata());
	}

	@Override
	public PlanDraft replan(ReplanRequest request) {
		Map<String, Object> metadata = new java.util.LinkedHashMap<>(
			request.originalPlan().snapshot().plannerMetadata());
		metadata.put("replanRequestId", request.id());
		metadata.put("failureClassification", request.failureClassification().name());
		return new PlanDraft(request.originalPlanId(), request.originalPlanVersion() + 1,
			request.originalPlan().goal(), request.originalPlan().steps(),
			request.originalPlan().dependencies(), request.originalPlan().snapshot(), name(),
			metadataValue(metadata, "model"), metadataValue(metadata, "promptVersion"), metadata);
	}

	private String metadataValue(Map<String, Object> metadata, String key) {
		Object value = metadata.get(key);
		return value instanceof String text ? text : null;
	}
}
