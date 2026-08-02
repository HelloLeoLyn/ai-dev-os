package com.aidevos.orchestrator.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PlanStep(String id, String name, String description, StepStatus status,
		AgentAssignment assignment, String toolProviderId, String toolName,
		Map<String, Object> toolArguments, List<ExpectedArtifact> expectedArtifacts,
		RetryPolicy retryPolicy, FailurePolicy failurePolicy, boolean skipApproval) {

	public PlanStep {
		status = status == null ? StepStatus.PLANNED : status;
		toolArguments = PlanValues.freezeMap(toolArguments);
		expectedArtifacts = expectedArtifacts == null ? List.of()
			: List.copyOf(new ArrayList<>(expectedArtifacts));
		retryPolicy = retryPolicy == null ? RetryPolicy.noRetry() : retryPolicy;
		failurePolicy = failurePolicy == null ? FailurePolicy.STOP_PLAN : failurePolicy;
	}
}
