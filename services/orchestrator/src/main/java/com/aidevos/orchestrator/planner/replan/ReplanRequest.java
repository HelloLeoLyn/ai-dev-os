package com.aidevos.orchestrator.planner.replan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.plan.Plan;

public record ReplanRequest(String id, String originalPlanId, int originalPlanVersion,
		String failedPlanRunId, String failedStepId,
		FailureClassification failureClassification, String failureReason,
		List<String> completedSteps, ExecutionRecordSummary executionRecord,
		List<ArtifactReference> artifactReferences, Plan originalPlan, Instant createdAt) {

	public ReplanRequest {
		completedSteps = completedSteps == null ? List.of()
			: List.copyOf(new ArrayList<>(completedSteps));
		artifactReferences = artifactReferences == null ? List.of()
			: List.copyOf(new ArrayList<>(artifactReferences));
	}

	public record ExecutionRecordSummary(String id, String taskId, String agentName,
			String status, String message) { }

	public record ArtifactReference(String type, String name, String mediaType, String uri) { }
}
