package com.aidevos.orchestrator.planner.replan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.PlanValidationResult;
import com.aidevos.orchestrator.plan.PlanValidator;
import org.springframework.stereotype.Component;

@Component
public class ReplanValidator {

	private final PlanValidator planValidator;

	public ReplanValidator(PlanValidator planValidator) {
		this.planValidator = planValidator;
	}

	public ReplanValidationResult validate(ReplanRequest request, Plan candidate) {
		List<String> errors = new ArrayList<>();
		List<String> approvalReasons = new ArrayList<>();
		PlanValidationResult planValidation = planValidator.validate(candidate);
		errors.addAll(planValidation.errors());
		if (request == null || request.originalPlan() == null) {
			errors.add("REPLAN_REQUEST_INVALID");
			return new ReplanValidationResult(false, errors, true,
				List.of("ORIGINAL_APPROVAL_NOT_REUSABLE"));
		}
		Plan original = request.originalPlan();
		if (candidate != null) {
			if (!original.id().equals(candidate.id())) {
				errors.add("REPLAN_PLAN_ID_MISMATCH");
			}
			if (candidate.version() != original.version() + 1) {
				errors.add("REPLAN_VERSION_MUST_INCREMENT");
			}
			Set<String> candidateSteps = new HashSet<>();
			candidate.steps().forEach(step -> candidateSteps.add(step.id()));
			for (String completed : request.completedSteps()) {
				if (!candidateSteps.contains(completed)) {
					errors.add("COMPLETED_STEP_MISSING:" + completed);
				}
			}
			if (!tools(candidate).equals(tools(original))
					&& !tools(original).containsAll(tools(candidate))) {
				approvalReasons.add("NEW_TOOL_REQUIRES_APPROVAL");
			}
			if (!permissions(original).containsAll(permissions(candidate))) {
				approvalReasons.add("NEW_PERMISSION_REQUIRES_APPROVAL");
			}
		}
		approvalReasons.add("ORIGINAL_APPROVAL_NOT_REUSABLE");
		return new ReplanValidationResult(errors.isEmpty(), errors, true,
			approvalReasons.stream().distinct().toList());
	}

	private Set<String> tools(Plan plan) {
		Set<String> tools = new HashSet<>();
		for (PlanStep step : plan.steps()) {
			if (step.toolProviderId() != null && step.toolName() != null) {
				tools.add(step.toolProviderId() + "/" + step.toolName());
			}
		}
		return tools;
	}

	private Set<String> permissions(Plan plan) {
		Set<String> permissions = new HashSet<>();
		plan.snapshot().agents().forEach(agent -> {
			if (agent.permissionLevel() != null && !agent.permissionLevel().isBlank()) {
				permissions.add(agent.permissionLevel());
			}
		});
		return permissions;
	}
}
