package com.aidevos.orchestrator.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanValidationResult;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import com.aidevos.orchestrator.planner.replan.ReplanValidationResult;
import com.aidevos.orchestrator.planner.replan.ReplanValidator;
import com.aidevos.orchestrator.planner.replan.ReplanningResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;

@Service
public class PlannerService {

	private final Map<String, Planner> planners;
	private final PlanValidator validator;
	private final ReplanValidator replanValidator;
	private final AuditService auditService;

	public PlannerService(List<Planner> planners, PlanValidator validator) {
		this(planners, validator, new ReplanValidator(validator), AuditService.noop());
	}

	public PlannerService(List<Planner> planners, PlanValidator validator,
			ReplanValidator replanValidator) {
		this(planners, validator, replanValidator, AuditService.noop());
	}

	@Autowired
	public PlannerService(List<Planner> planners, PlanValidator validator,
			ReplanValidator replanValidator, AuditService auditService) {
		this.planners = index(planners);
		this.validator = validator;
		this.replanValidator = replanValidator;
		this.auditService = auditService;
	}

	public ReplanningResult replan(String plannerName, ReplanRequest request) {
		Planner planner = planners.get(plannerName);
		if (planner == null) {
			return ReplanningResult.failure(plannerName, null,
				List.of("PLANNER_NOT_FOUND:" + plannerName));
		}
		if (request == null) {
			return ReplanningResult.failure(plannerName, null,
				List.of("REPLAN_REQUEST_REQUIRED"));
		}
		try {
			PlanDraft draft = planner.replan(request);
			if (draft == null) {
				return ReplanningResult.failure(plannerName, null,
					List.of("PLANNER_RETURNED_NO_DRAFT"));
			}
			Plan candidate = draft.toPlan();
			ReplanValidationResult validation = replanValidator.validate(request, candidate);
			if (!validation.valid()) {
				return new ReplanningResult(false, plannerName, draft, null, validation.errors(),
					validation.newApprovalRequired(), validation.approvalReasons());
			}
			return new ReplanningResult(true, plannerName, draft, candidate, List.of(),
				validation.newApprovalRequired(), validation.approvalReasons());
		}
		catch (RuntimeException exception) {
			return ReplanningResult.failure(plannerName, null,
				List.of("PLANNER_FAILED:" + exception.getClass().getSimpleName()));
		}
	}

	public PlanningResult createPlan(PlanningRequest request) {
		if (request == null) {
			return PlanningResult.failure(null, null, List.of("PLANNING_REQUEST_REQUIRED"));
		}
		Planner planner = planners.get(request.plannerName());
		if (planner == null) {
			return PlanningResult.failure(request.plannerName(), null,
				List.of("PLANNER_NOT_FOUND:" + request.plannerName()));
		}
		try {
			PlanDraft draft = planner.plan(request);
			if (draft == null) {
				return PlanningResult.failure(planner.name(), null,
					List.of("PLANNER_RETURNED_NO_DRAFT"));
			}
			Plan candidate = draft.toPlan();
			PlanValidationResult validation = validator.validate(candidate);
			if (!validation.valid()) {
				auditService.planEvent(EventType.PLAN_VALIDATION_FAILED, request, "FAILED",
					validation.errors());
				return PlanningResult.failure(planner.name(), draft, validation.errors());
			}
			auditService.planEvent(EventType.PLAN_CREATED, request, "CREATED", List.of());
			return PlanningResult.success(planner.name(), draft, candidate);
		}
		catch (RuntimeException exception) {
			return PlanningResult.failure(planner.name(), null,
				List.of("PLANNER_FAILED:" + exception.getClass().getSimpleName()));
		}
	}

	private Map<String, Planner> index(List<Planner> plannerList) {
		Map<String, Planner> indexed = new LinkedHashMap<>();
		for (Planner planner : plannerList) {
			if (planner == null || planner.name() == null || planner.name().isBlank()) {
				throw new IllegalArgumentException("Planner name is required");
			}
			if (indexed.putIfAbsent(planner.name(), planner) != null) {
				throw new IllegalArgumentException("Duplicate planner: " + planner.name());
			}
		}
		return Map.copyOf(indexed);
	}
}
