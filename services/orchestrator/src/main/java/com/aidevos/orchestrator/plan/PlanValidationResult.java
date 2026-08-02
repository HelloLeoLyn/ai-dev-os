package com.aidevos.orchestrator.plan;

import java.util.ArrayList;
import java.util.List;

public record PlanValidationResult(boolean valid, List<String> errors) {

	public PlanValidationResult {
		errors = errors == null ? List.of() : List.copyOf(new ArrayList<>(errors));
	}
}
