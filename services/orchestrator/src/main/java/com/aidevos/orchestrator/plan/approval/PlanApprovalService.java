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
import com.aidevos.orchestrator.plan.PlanValidationResult;
import com.aidevos.orchestrator.plan.PlanValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class PlanApprovalService {

	private final PlanApprovalStore store;
	private final PlanValidator validator;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	@Autowired
	public PlanApprovalService(PlanApprovalStore store, PlanValidator validator,
			ObjectMapper objectMapper) {
		this(store, validator, objectMapper, Clock.systemUTC());
	}

	PlanApprovalService(PlanApprovalStore store, PlanValidator validator,
			ObjectMapper objectMapper, Clock clock) {
		this.store = store;
		this.validator = validator;
		this.objectMapper = objectMapper;
		this.clock = clock;
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
		return store.save(request);
	}

	public PlanApprovalRequest approve(String id, String approver) {
		PlanApprovalRequest request = requireRequest(id);
		request.approve(approver, Instant.now(clock));
		return request;
	}

	public PlanApprovalRequest reject(String id, String approver, String reason) {
		PlanApprovalRequest request = requireRequest(id);
		request.reject(approver, reason, Instant.now(clock));
		return request;
	}

	public PlanApprovalRequest consume(String id) {
		PlanApprovalRequest request = requireRequest(id);
		request.consume();
		return request;
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
