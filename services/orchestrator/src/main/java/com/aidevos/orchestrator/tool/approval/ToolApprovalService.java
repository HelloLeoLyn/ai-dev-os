package com.aidevos.orchestrator.tool.approval;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ToolApprovalService {

	private final ToolApprovalStore store;
	private final ObjectMapper objectMapper;

	public ToolApprovalService(ToolApprovalStore store, ObjectMapper objectMapper) {
		this.store = store;
		this.objectMapper = objectMapper;
	}

	public ToolApprovalDecision authorize(ToolInvocation invocation, ToolDefinition definition,
			String reason) {
		String argumentsHash = hash(invocation.arguments());
		String permissionLevel = definition.access().name().toLowerCase().replace('_', '-');
		ToolApprovalRequest existing = store.findReusable(invocation.jobId(), invocation.invocationId(),
			invocation.providerId(), invocation.toolName(), argumentsHash, invocation.workspace(),
			permissionLevel);
		if (existing != null && existing.consume()) {
			return new ToolApprovalDecision(false, existing.getId());
		}
		if (existing != null) {
			return new ToolApprovalDecision(true, existing.getId());
		}
		ToolApprovalRequest request = new ToolApprovalRequest(UUID.randomUUID().toString(),
			invocation.executionId(), invocation.invocationId(), invocation.jobId(),
			invocation.providerId(), invocation.toolName(), argumentsHash, invocation.workspace(),
			permissionLevel, reason);
		store.save(request);
		return new ToolApprovalDecision(true, request.getId());
	}

	public ToolApprovalRequest approve(String id) {
		ToolApprovalRequest request = store.get(id);
		if (request != null) {
			request.approve();
		}
		return request;
	}

	public ToolApprovalRequest reject(String id) {
		ToolApprovalRequest request = store.get(id);
		if (request != null) {
			request.reject();
		}
		return request;
	}

	public List<ToolApprovalRequest> getAll() {
		return store.getAll();
	}

	private String hash(Map<String, Object> arguments) {
		try {
			byte[] canonical = objectMapper.writeValueAsBytes(normalize(arguments));
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private Object normalize(Object value) {
		if (value instanceof Map<?, ?> source) {
			Map<String, Object> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> entry : source.entrySet()) {
				if (entry.getKey() instanceof String key) {
					sorted.put(key, normalize(entry.getValue()));
				}
			}
			return sorted;
		}
		if (value instanceof List<?> source) {
			List<Object> normalized = new ArrayList<>();
			source.forEach(item -> normalized.add(normalize(item)));
			return normalized;
		}
		return value;
	}
}
