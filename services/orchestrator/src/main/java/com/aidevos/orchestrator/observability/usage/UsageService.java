package com.aidevos.orchestrator.observability.usage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Records and aggregates model usage (tokens + estimated cost) per task,
 * project and agent. Cost is estimated with a default per-token rate so no
 * real billing integration is required.
 */
@Service
public class UsageService {

	/** Default input token price in USD per 1k tokens. */
	private static final double INPUT_PRICE_PER_1K = 0.003;

	/** Default output token price in USD per 1k tokens. */
	private static final double OUTPUT_PRICE_PER_1K = 0.015;

	private final UsageRepository repository;
	private final AuditService auditService;

	public UsageService(UsageRepository repository) {
		this(repository, AuditService.noop());
	}

	@Autowired
	public UsageService(UsageRepository repository, AuditService auditService) {
		this.repository = repository;
		this.auditService = auditService;
	}

	public UsageRecord recordUsage(String taskId, String projectId, String agentType,
			String model, long inputTokens, long outputTokens) {
		long total = Math.max(0, inputTokens) + Math.max(0, outputTokens);
		double cost = Math.max(0, inputTokens) * INPUT_PRICE_PER_1K / 1000.0
			+ Math.max(0, outputTokens) * OUTPUT_PRICE_PER_1K / 1000.0;
		UsageRecord record = new UsageRecord("usage-" + UUID.randomUUID(), taskId, projectId,
			agentType, model, Math.max(0, inputTokens), Math.max(0, outputTokens), total,
			cost, Instant.now());
		repository.save(record);
		auditService.usageEvent(EventType.USAGE_RECORDED, record.usageId(), taskId, projectId,
			agentType, record.inputTokens(), record.outputTokens(), record.totalTokens(),
			record.estimatedCost(), "Usage recorded");
		return record;
	}

	public UsageSummary getTaskUsage(String taskId) {
		return summarize(record -> taskId != null && taskId.equals(record.taskId()));
	}

	public UsageSummary getProjectUsage(String projectId) {
		return summarize(record -> projectId != null && projectId.equals(record.projectId()));
	}

	public UsageSummary getAgentUsage(String agentType) {
		return summarize(record -> agentType != null && agentType.equals(record.agentType()));
	}

	public List<UsageRecord> listUsage() {
		return repository.list();
	}

	private UsageSummary summarize(java.util.function.Predicate<UsageRecord> filter) {
		long records = 0;
		long input = 0;
		long output = 0;
		long total = 0;
		double cost = 0;
		for (UsageRecord record : repository.list()) {
			if (filter.test(record)) {
				records++;
				input += record.inputTokens();
				output += record.outputTokens();
				total += record.totalTokens();
				cost += record.estimatedCost();
			}
		}
		return new UsageSummary(records, input, output, total, cost);
	}
}
