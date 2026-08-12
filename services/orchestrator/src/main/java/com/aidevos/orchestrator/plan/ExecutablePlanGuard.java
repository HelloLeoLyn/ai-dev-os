package com.aidevos.orchestrator.plan;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.tool.ToolAccess;

/** Runtime contract checks for plans that must perform real project analysis. */
public final class ExecutablePlanGuard {

	private ExecutablePlanGuard() {
	}

	public static List<String> errors(Plan plan) {
		if (plan == null || plan.snapshot() == null
				|| !"project-analysis".equals(plan.snapshot().plannerMetadata().get("taskType"))) {
			return List.of();
		}
		List<String> errors = new ArrayList<>();
		for (PlanStep step : plan.steps()) {
			var agent = plan.snapshot().agents().stream()
				.filter(candidate -> step.assignment() != null
					&& candidate.name().equals(step.assignment().agentName()))
				.findFirst().orElse(null);
			if (agent == null) {
				errors.add("PROJECT_ANALYSIS_AGENT_REQUIRED:" + step.id());
				continue;
			}
			if ("mock".equals(agent.executor())) {
				errors.add("PROJECT_ANALYSIS_MOCK_EXECUTOR_FORBIDDEN:" + step.id());
			}
			if (!agent.capabilities().contains("analysis")) {
				errors.add("PROJECT_ANALYSIS_CAPABILITY_REQUIRED:" + step.id());
			}
			if (!"read-only".equals(agent.permissionLevel())) {
				errors.add("READ_ONLY_AGENT_REQUIRED:" + step.id());
			}
			if ("codex".equals(agent.executor())) {
				boolean supported = step.expectedArtifacts().stream().allMatch(artifact ->
					"codex-result".equals(artifact.type())
						&& (artifact.mediaType() == null
							|| "text/plain".equals(artifact.mediaType())));
				if (!supported) {
					errors.add("EXPECTED_ARTIFACT_UNSUPPORTED_BY_EXECUTOR:" + step.id());
				}
			}
			if (step.parameters().values().stream()
					.anyMatch(value -> "workspace-write".equals(String.valueOf(value)))) {
				errors.add("READ_ONLY_WORKSPACE_WRITE_FORBIDDEN:" + step.id());
			}
			if (step.toolProviderId() != null && step.toolName() != null) {
				plan.snapshot().tools().stream()
					.filter(tool -> tool.providerId().equals(step.toolProviderId())
						&& tool.name().equals(step.toolName()))
					.filter(tool -> tool.access() == ToolAccess.WORKSPACE_WRITE)
					.findAny().ifPresent(tool ->
						errors.add("READ_ONLY_WRITE_TOOL_FORBIDDEN:" + step.id()));
			}
		}
		return List.copyOf(errors);
	}

	public static void requireExecutable(Plan plan) {
		List<String> errors = errors(plan);
		if (!errors.isEmpty()) {
			throw new IllegalStateException(String.join(", ", errors));
		}
	}
}
