package com.aidevos.orchestrator.tool.approval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class ToolApprovalStore implements ToolApprovalRepository {

	private final Map<String, ToolApprovalRequest> requests = new LinkedHashMap<>();

	public synchronized void save(ToolApprovalRequest request) {
		requests.put(request.getId(), request);
	}

	public synchronized ToolApprovalRequest get(String id) {
		return requests.get(id);
	}

	public synchronized List<ToolApprovalRequest> getAll() {
		return new ArrayList<>(requests.values());
	}

	public synchronized ToolApprovalRequest findReusable(String jobId, String invocationId,
			String providerId, String toolName, String argumentsHash, String workspace,
			String permissionLevel) {
		return requests.values().stream()
			.filter(request -> same(request.getJobId(), jobId))
			.filter(request -> request.getInvocationId().equals(invocationId))
			.filter(request -> request.getProviderId().equals(providerId))
			.filter(request -> request.getToolName().equals(toolName))
			.filter(request -> request.getArgumentsHash().equals(argumentsHash))
			.filter(request -> same(request.getWorkspace(), workspace))
			.filter(request -> request.getPermissionLevel().equals(permissionLevel))
			.filter(request -> request.getStatus() == ApprovalStatus.PENDING
				|| request.getStatus() == ApprovalStatus.APPROVED)
			.reduce((first, second) -> second)
			.orElse(null);
	}

	private boolean same(String first, String second) {
		return first == null ? second == null : first.equals(second);
	}
}
