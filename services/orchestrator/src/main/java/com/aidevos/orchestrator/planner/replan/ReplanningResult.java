package com.aidevos.orchestrator.planner.replan;

import java.util.List;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.planner.PlanDraft;

public record ReplanningResult(boolean success, String plannerName, PlanDraft draft, Plan plan,
		List<String> errors, boolean newApprovalRequired, List<String> approvalReasons) {

	public static ReplanningResult failure(String plannerName, PlanDraft draft,
			List<String> errors) {
		return new ReplanningResult(false, plannerName, draft, null, List.copyOf(errors), true,
			List.of("ORIGINAL_APPROVAL_NOT_REUSABLE"));
	}
}
