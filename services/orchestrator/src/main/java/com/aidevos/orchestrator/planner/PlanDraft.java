package com.aidevos.orchestrator.planner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;

public record PlanDraft(String planId, int version, String goal, List<PlanStep> steps,
		List<Dependency> dependencies, PlanSnapshot snapshot, String plannerName, String model,
		String promptVersion, Map<String, Object> plannerMetadata) {

	public PlanDraft {
		steps = steps == null ? List.of() : List.copyOf(new ArrayList<>(steps));
		dependencies = dependencies == null ? List.of()
			: List.copyOf(new ArrayList<>(dependencies));
		plannerMetadata = plannerMetadata == null || plannerMetadata.isEmpty()
			? Map.of() : Map.copyOf(new LinkedHashMap<>(plannerMetadata));
	}

	public Plan toPlan() {
		return new Plan(planId, version, goal, PlanStatus.DRAFT, steps, dependencies, snapshot,
			Instant.now());
	}
}
