package com.aidevos.orchestrator.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record Plan(String id, int version, String goal, PlanStatus status,
		List<PlanStep> steps, List<Dependency> dependencies, PlanSnapshot snapshot,
		Instant createdAt) {

	public Plan {
		steps = steps == null ? List.of() : List.copyOf(new ArrayList<>(steps));
		dependencies = dependencies == null ? List.of()
			: List.copyOf(new ArrayList<>(dependencies));
		status = status == null ? PlanStatus.DRAFT : status;
		createdAt = createdAt == null ? Instant.now() : createdAt;
	}
}
