package com.aidevos.orchestrator.planner;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.plan.Plan;

public record PlanningResult(boolean success, String plannerName, PlanDraft draft, Plan plan,
		List<String> errors) {

	public PlanningResult {
		errors = errors == null ? List.of() : List.copyOf(new ArrayList<>(errors));
	}

	public static PlanningResult success(String plannerName, PlanDraft draft, Plan plan) {
		return new PlanningResult(true, plannerName, draft, plan, List.of());
	}

	public static PlanningResult failure(String plannerName, PlanDraft draft,
			List<String> errors) {
		return new PlanningResult(false, plannerName, draft, null, errors);
	}
}
