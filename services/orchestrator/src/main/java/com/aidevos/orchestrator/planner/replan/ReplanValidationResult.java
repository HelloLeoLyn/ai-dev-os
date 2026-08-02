package com.aidevos.orchestrator.planner.replan;

import java.util.ArrayList;
import java.util.List;

public record ReplanValidationResult(boolean valid, List<String> errors,
		boolean newApprovalRequired, List<String> approvalReasons) {

	public ReplanValidationResult {
		errors = errors == null ? List.of() : List.copyOf(new ArrayList<>(errors));
		approvalReasons = approvalReasons == null ? List.of()
			: List.copyOf(new ArrayList<>(approvalReasons));
	}
}
