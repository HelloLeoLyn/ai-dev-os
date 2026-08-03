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
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ToolApprovalService {

	private final ToolApprovalRepository store;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;

	public ToolApprovalService(ToolApprovalRepository store, ObjectMapper objectMapper) {
		this(store, objectMapper, AuditService.noop());
	}

	@Autowired
	public ToolApprovalService(ToolApprovalRepository store, ObjectMapper objectMapper,
			AuditService auditService) {
		this.store = store;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	public ToolApprovalDecision authorize(ToolInvocation invocation, ToolDefinition definition,
			String reason) {
		String argumentsHash = hash(invocation.arguments());
		String permissionLevel = definition.access().name().toLowerCase().replace('_', '-');
		ToolApprovalRequest existing = store.findReusable(invocation.jobId(), invocation.invocationId(),
			invocation.providerId(), invocation.toolName(), argumentsHash, invocation.workspace(),
			permissionLevel);
		if (existing != null && existing.consume()) {
			store.save(existing);
			auditService.toolApprovalEvent(EventType.TOOL_APPROVAL_CONSUMED, existing,
				ApprovalStatus.APPROVED.name(), existing.getStatus().name());
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
		auditService.toolApprovalEvent(EventType.TOOL_APPROVAL_REQUESTED, request, null,
			request.getStatus().name());
		return new ToolApprovalDecision(true, request.getId());
	}

	public ToolApprovalRequest approve(String id) {
		ToolApprovalRequest request = store.get(id);
		if (request != null) {
			ApprovalStatus before = request.getStatus();
			request.approve();
			store.save(request);
			if (before != request.getStatus()) {
				auditService.toolApprovalEvent(EventType.TOOL_APPROVAL_APPROVED, request,
					before.name(), request.getStatus().name());
			}
		}
		return request;
	}

	public ToolApprovalRequest reject(String id) {
		ToolApprovalRequest request = store.get(id);
		if (request != null) {
			request.reject();
			store.save(request);
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
