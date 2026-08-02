package com.aidevos.orchestrator.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PlanValidator {

	public static final int MAX_RETRY_ATTEMPTS = 3;

	public PlanValidationResult validate(Plan plan) {
		List<String> errors = new ArrayList<>();
		if (plan == null) {
			return new PlanValidationResult(false, List.of("PLAN_REQUIRED"));
		}
		validatePlan(plan, errors);
		return new PlanValidationResult(errors.isEmpty(), errors);
	}

	private void validatePlan(Plan plan, List<String> errors) {
		if (blank(plan.id())) {
			errors.add("PLAN_ID_REQUIRED");
		}
		if (plan.version() < 1) {
			errors.add("PLAN_VERSION_INVALID");
		}
		if (blank(plan.goal())) {
			errors.add("PLAN_GOAL_REQUIRED");
		}
		if (plan.snapshot() == null) {
			errors.add("PLAN_SNAPSHOT_REQUIRED");
			return;
		}
		if (blank(plan.snapshot().policyVersion())) {
			errors.add("POLICY_VERSION_REQUIRED");
		}

		Map<String, PlanStep> steps = collectSteps(plan.steps(), errors);
		for (PlanStep step : plan.steps()) {
			validateStep(step, plan.snapshot(), errors);
		}
		validateDependencies(plan.dependencies(), steps, errors);
		validateArtifactReferences(plan, steps, errors);
		validateDag(plan.dependencies(), steps.keySet(), errors);
	}

	private Map<String, PlanStep> collectSteps(List<PlanStep> planSteps, List<String> errors) {
		Map<String, PlanStep> steps = new HashMap<>();
		if (planSteps.isEmpty()) {
			errors.add("PLAN_STEPS_REQUIRED");
			return steps;
		}
		for (PlanStep step : planSteps) {
			if (step == null || blank(step.id())) {
				errors.add("STEP_ID_REQUIRED");
				continue;
			}
			if (steps.putIfAbsent(step.id(), step) != null) {
				errors.add("DUPLICATE_STEP_ID:" + step.id());
			}
		}
		return steps;
	}

	private void validateStep(PlanStep step, PlanSnapshot snapshot, List<String> errors) {
		if (step == null) {
			return;
		}
		if (step.status() != StepStatus.PLANNED) {
			errors.add("STEP_STATUS_INVALID:" + step.id());
		}
		validateAssignment(step, snapshot, errors);
		validateTool(step, snapshot, errors);
		validateRetry(step, errors);
		validateArtifacts(step, errors);
		if (step.skipApproval()) {
			errors.add("APPROVAL_BYPASS_FORBIDDEN:" + step.id());
		}
	}

	private void validateAssignment(PlanStep step, PlanSnapshot snapshot, List<String> errors) {
		AgentAssignment assignment = step.assignment();
		if (assignment == null || (blank(assignment.agentName())
				&& assignment.requiredCapabilities().isEmpty())) {
			errors.add("AGENT_OR_CAPABILITY_REQUIRED:" + step.id());
			return;
		}
		if (!blank(assignment.agentName())) {
			PlanSnapshot.AgentSnapshot agent = findAgent(snapshot, assignment.agentName());
			if (agent == null) {
				errors.add("UNKNOWN_AGENT:" + assignment.agentName());
			}
			else {
				validateAgent(agent, assignment.requiredCapabilities(), snapshot, errors);
			}
		}
		for (String capability : assignment.requiredCapabilities()) {
			if (!snapshot.capabilities().contains(capability)) {
				errors.add("UNKNOWN_CAPABILITY:" + capability);
			}
		}
		for (String fallback : assignment.fallbackAgentNames()) {
			PlanSnapshot.AgentSnapshot agent = findAgent(snapshot, fallback);
			if (agent == null) {
				errors.add("UNKNOWN_AGENT:" + fallback);
			}
			else {
				validateAgent(agent, assignment.requiredCapabilities(), snapshot, errors);
			}
		}
	}

	private void validateAgent(PlanSnapshot.AgentSnapshot agent, List<String> capabilities,
			PlanSnapshot snapshot, List<String> errors) {
		if (!agent.enabled()) {
			errors.add("AGENT_DISABLED:" + agent.name());
		}
		if (blank(agent.executor()) || !snapshot.executors().contains(agent.executor())) {
			errors.add("UNKNOWN_EXECUTOR:" + agent.executor());
		}
		if (!agent.capabilities().containsAll(capabilities)) {
			errors.add("AGENT_CAPABILITY_MISMATCH:" + agent.name());
		}
	}

	private void validateTool(PlanStep step, PlanSnapshot snapshot, List<String> errors) {
		boolean hasProvider = !blank(step.toolProviderId());
		boolean hasName = !blank(step.toolName());
		boolean hasArguments = !step.toolArguments().isEmpty();
		if (!hasProvider && !hasName && !hasArguments) {
			return;
		}
		if (!hasProvider || !hasName) {
			errors.add("TOOL_DECLARATION_INCOMPLETE:" + step.id());
			return;
		}
		boolean known = snapshot.tools().stream().anyMatch(tool ->
			tool.providerId().equals(step.toolProviderId()) && tool.name().equals(step.toolName()));
		if (!known) {
			errors.add("UNKNOWN_TOOL:" + step.toolProviderId() + "/" + step.toolName());
		}
	}

	private void validateRetry(PlanStep step, List<String> errors) {
		RetryPolicy retry = step.retryPolicy();
		if (retry.maxAttempts() < 1 || retry.maxAttempts() > MAX_RETRY_ATTEMPTS) {
			errors.add("RETRY_LIMIT_INVALID:" + step.id());
		}
		if (retry.initialDelay().isNegative()) {
			errors.add("RETRY_DELAY_INVALID:" + step.id());
		}
	}

	private void validateArtifacts(PlanStep step, List<String> errors) {
		for (ExpectedArtifact artifact : step.expectedArtifacts()) {
			if (artifact == null || blank(artifact.type())) {
				errors.add("EXPECTED_ARTIFACT_TYPE_REQUIRED:" + step.id());
			}
			else if (artifact.minimumCount() < 0
					|| (artifact.required() && artifact.minimumCount() < 1)) {
				errors.add("EXPECTED_ARTIFACT_COUNT_INVALID:" + step.id());
			}
		}
	}

	private void validateDependencies(List<Dependency> dependencies, Map<String, PlanStep> steps,
			List<String> errors) {
		Set<String> seen = new HashSet<>();
		for (Dependency dependency : dependencies) {
			if (dependency == null || blank(dependency.fromStepId()) || blank(dependency.toStepId())) {
				errors.add("DEPENDENCY_INVALID");
				continue;
			}
			if (!steps.containsKey(dependency.fromStepId())
					|| !steps.containsKey(dependency.toStepId())) {
				errors.add("DEPENDENCY_STEP_UNKNOWN:" + dependency.fromStepId()
					+ "->" + dependency.toStepId());
			}
			if (dependency.fromStepId().equals(dependency.toStepId())) {
				errors.add("DEPENDENCY_SELF_REFERENCE:" + dependency.fromStepId());
			}
			String key = dependency.fromStepId() + "\u0000" + dependency.toStepId();
			if (!seen.add(key)) {
				errors.add("DEPENDENCY_DUPLICATE:" + dependency.fromStepId()
					+ "->" + dependency.toStepId());
			}
		}
	}

	private void validateArtifactReferences(Plan plan, Map<String, PlanStep> steps,
			List<String> errors) {
		Set<String> dependencyKeys = new HashSet<>();
		plan.dependencies().stream().filter(java.util.Objects::nonNull)
			.forEach(dependency -> dependencyKeys.add(dependency.fromStepId() + "\u0000"
				+ dependency.toStepId()));
		for (PlanStep step : plan.steps()) {
			if (step == null) {
				continue;
			}
			Set<String> inputKeys = new HashSet<>();
			for (ArtifactReference reference : step.inputArtifacts()) {
				if (reference == null || blank(reference.fromStepId())
						|| blank(reference.artifactType()) || blank(reference.inputKey())) {
					errors.add("ARTIFACT_REFERENCE_INCOMPLETE:" + step.id());
					continue;
				}
				if (!inputKeys.add(reference.inputKey())) {
					errors.add("ARTIFACT_INPUT_KEY_DUPLICATE:" + step.id() + ":"
						+ reference.inputKey());
				}
				PlanStep source = steps.get(reference.fromStepId());
				if (source == null) {
					errors.add("ARTIFACT_SOURCE_STEP_UNKNOWN:" + reference.fromStepId());
					continue;
				}
				if (!dependencyKeys.contains(reference.fromStepId() + "\u0000" + step.id())) {
					errors.add("ARTIFACT_SOURCE_DEPENDENCY_REQUIRED:" + reference.fromStepId()
						+ "->" + step.id());
				}
				boolean declared = source.expectedArtifacts().stream().anyMatch(artifact ->
					artifact != null && reference.artifactType().equals(artifact.type())
						&& (blank(reference.artifactName())
							|| reference.artifactName().equals(artifact.name())));
				if (!declared) {
					errors.add("ARTIFACT_SOURCE_NOT_DECLARED:" + reference.fromStepId() + ":"
						+ reference.artifactType());
				}
			}
		}
	}

	private void validateDag(List<Dependency> dependencies, Set<String> stepIds,
			List<String> errors) {
		Map<String, Integer> indegree = new HashMap<>();
		Map<String, List<String>> outgoing = new HashMap<>();
		stepIds.forEach(id -> {
			indegree.put(id, 0);
			outgoing.put(id, new ArrayList<>());
		});
		for (Dependency dependency : dependencies) {
			if (dependency == null || !stepIds.contains(dependency.fromStepId())
					|| !stepIds.contains(dependency.toStepId())) {
				continue;
			}
			outgoing.get(dependency.fromStepId()).add(dependency.toStepId());
			indegree.computeIfPresent(dependency.toStepId(), (key, value) -> value + 1);
		}
		ArrayDeque<String> ready = new ArrayDeque<>();
		indegree.forEach((id, degree) -> {
			if (degree == 0) {
				ready.add(id);
			}
		});
		int visited = 0;
		while (!ready.isEmpty()) {
			String current = ready.remove();
			visited++;
			for (String target : outgoing.get(current)) {
				int degree = indegree.computeIfPresent(target, (key, value) -> value - 1);
				if (degree == 0) {
					ready.add(target);
				}
			}
		}
		if (visited != stepIds.size()) {
			errors.add("PLAN_DEPENDENCY_CYCLE");
		}
	}

	private PlanSnapshot.AgentSnapshot findAgent(PlanSnapshot snapshot, String name) {
		return snapshot.agents().stream().filter(agent -> agent.name().equals(name))
			.findFirst().orElse(null);
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
