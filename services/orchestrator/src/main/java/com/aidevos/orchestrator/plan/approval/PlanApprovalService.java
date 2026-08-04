package com.aidevos.orchestrator.plan.approval;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.plan.PlanValidationResult;
import com.aidevos.orchestrator.plan.PlanValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlanApprovalService {

	private final PlanApprovalRepository store;
	private final PlanValidator validator;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final AuditService auditService;

	@Autowired
	public PlanApprovalService(PlanApprovalRepository store, PlanValidator validator,
			ObjectMapper objectMapper, AuditService auditService) {
		this(store, validator, objectMapper, Clock.systemUTC(), auditService);
	}

	PlanApprovalService(PlanApprovalRepository store, PlanValidator validator,
			ObjectMapper objectMapper, Clock clock) {
		this(store, validator, objectMapper, clock, AuditService.noop());
	}

	PlanApprovalService(PlanApprovalRepository store, PlanValidator validator,
			ObjectMapper objectMapper, Clock clock, AuditService auditService) {
		this.store = store;
		this.validator = validator;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.auditService = auditService;
	}

	public PlanApprovalRequest create(String requestId, Plan plan) {
		if (requestId == null || requestId.isBlank()) {
			throw new IllegalArgumentException("Request id is required");
		}
		PlanValidationResult validation = validator.validate(plan);
		if (!validation.valid()) {
			throw new IllegalArgumentException("Invalid plan: " + String.join(",", validation.errors()));
		}
		String hash = hash(plan);
		PlanApprovalRequest request = new PlanApprovalRequest(UUID.randomUUID().toString(),
			requestId, plan, hash, Instant.now(clock));
		PlanApprovalRequest saved = store.save(request);
		auditService.planApprovalEvent(EventType.PLAN_APPROVAL_REQUESTED, saved, null,
			ApprovalStatus.PENDING.name());
		return saved;
	}

	public PlanApprovalRequest approve(String id, String approver) {
		PlanApprovalRequest request = requireRequest(id);
		request.approve(approver, Instant.now(clock));
		PlanApprovalRequest saved = store.save(request);
		auditService.planApprovalEvent(EventType.PLAN_APPROVAL_APPROVED, saved,
			ApprovalStatus.PENDING.name(), ApprovalStatus.APPROVED.name());
		return saved;
	}

	public PlanApprovalRequest reject(String id, String approver, String reason) {
		PlanApprovalRequest request = requireRequest(id);
		request.reject(approver, reason, Instant.now(clock));
		PlanApprovalRequest saved = store.save(request);
		auditService.planApprovalEvent(EventType.PLAN_APPROVAL_REJECTED, saved,
			ApprovalStatus.PENDING.name(), ApprovalStatus.REJECTED.name());
		return saved;
	}

	public PlanApprovalRequest consume(String id) {
		requireRequest(id);
		if (!store.consumeIfApproved(id)) {
			PlanApprovalRequest current = requireRequest(id);
			if (current.getStatus() == ApprovalStatus.CONSUMED) {
				return current;
			}
			throw new IllegalStateException("Plan approval cannot be consumed from state: "
				+ current.getStatus());
		}
		PlanApprovalRequest saved = requireRequest(id);
		auditService.planApprovalEvent(EventType.PLAN_APPROVAL_CONSUMED, saved,
			ApprovalStatus.APPROVED.name(), ApprovalStatus.CONSUMED.name());
		return saved;
	}

	public PlanApprovalRequest get(String id) {
		return store.get(id);
	}

	public List<PlanApprovalRequest> getAll() {
		return store.getAll();
	}

	private PlanApprovalRequest requireRequest(String id) {
		PlanApprovalRequest request = store.get(id);
		if (request == null) {
			throw new PlanApprovalNotFoundException(id);
		}
		return request;
	}

	private String hash(Plan plan) {
		try {
			Map<String, Object> definition = new TreeMap<>();
			definition.put("planId", plan.id());
			definition.put("planVersion", plan.version());
			definition.put("goal", plan.goal());
			definition.put("steps", plan.steps());
			definition.put("dependencies", plan.dependencies());
			definition.put("snapshot", plan.snapshot());
			byte[] canonical = objectMapper.writeValueAsBytes(normalize(definition));
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private Object normalize(Object value) {
		Object converted = objectMapper.convertValue(value, Object.class);
		if (converted instanceof Map<?, ?> source) {
			Map<String, Object> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> entry : source.entrySet()) {
				if (entry.getKey() instanceof String key) {
					sorted.put(key, normalize(entry.getValue()));
				}
			}
			return sorted;
		}
		if (converted instanceof List<?> source) {
			List<Object> normalized = new ArrayList<>();
			source.forEach(item -> normalized.add(normalize(item)));
			return normalized;
		}
		return converted;
	}

	public static class PlanApprovalNotFoundException extends RuntimeException {
		public PlanApprovalNotFoundException(String id) {
			super("Plan approval not found: " + id);
		}
	}
}
