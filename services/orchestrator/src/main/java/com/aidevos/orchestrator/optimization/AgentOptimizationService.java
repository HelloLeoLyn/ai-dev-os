package com.aidevos.orchestrator.optimization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentMessageRepository;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.collaboration.AgentTeamRepository;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanApprovalRepository;
import com.aidevos.orchestrator.human.HumanApprovalStatus;
import com.aidevos.orchestrator.metrics.agent.AgentMetrics;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.usage.UsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent scoring for autonomous selection. A composite AgentScore is derived
 * on demand from the existing AgentMetrics (executions, success/failure,
 * duration), the collaboration team/message stores (team participation) and
 * the human approval store (approval rate); trace and usage figures are
 * carried into the audit metadata. Scores are never persisted; AGENT_SCORE_
 * UPDATED audit events record each computed score.
 */
@Service
public class AgentOptimizationService {

	private final AgentMetricsService metricsService;
	private final ExecutionTraceService traceService;
	private final UsageService usageService;
	private final AgentTeamRepository teamRepository;
	private final AgentMessageRepository messageRepository;
	private final HumanApprovalRepository approvalRepository;
	private final AuditService auditService;

	@Autowired
	public AgentOptimizationService(AgentMetricsService metricsService,
			ExecutionTraceService traceService, UsageService usageService,
			AgentTeamRepository teamRepository, AgentMessageRepository messageRepository,
			HumanApprovalRepository approvalRepository, AuditService auditService) {
		this.metricsService = metricsService;
		this.traceService = traceService;
		this.usageService = usageService;
		this.teamRepository = teamRepository;
		this.messageRepository = messageRepository;
		this.approvalRepository = approvalRepository;
		this.auditService = auditService;
	}

	/**
	 * Scores and ranks every known agent, best composite first. Agents come
	 * from the existing AgentMetrics ranking so only agents that actually
	 * executed are scored.
	 */
	public List<AgentScore> scoreAllAgents() {
		List<AgentMetrics> metrics = metricsService == null
			? List.of() : metricsService.listAgentMetrics();
		List<AgentScore> scores = new ArrayList<>();
		for (AgentMetrics metric : metrics) {
			if (metric.agentName() == null || metric.agentName().isBlank()) {
				continue;
			}
			scores.add(scoreAgent(metric.agentName()));
		}
		scores.sort(Comparator.comparingDouble(AgentScore::composite).reversed()
			.thenComparing(AgentScore::agentType));
		return scores;
	}

	/**
	 * Computes the composite score of one agent type: execution statistics
	 * from AgentMetrics, collaboration score from the teams the agent joined
	 * and human approval rate from the approvals it requested. Audits the
	 * AGENT_SCORE_UPDATED event with the score and its inputs.
	 */
	public AgentScore scoreAgent(String agentType) {
		if (agentType == null || agentType.isBlank()) {
			throw new IllegalArgumentException("Agent type is required");
		}
		AgentMetrics metric = metricFor(agentType);
		int total = metric == null ? 0 : metric.taskCount();
		int success = metric == null ? 0 : metric.successCount();
		int failed = metric == null ? 0 : metric.failedCount();
		long avgDuration = metric == null ? 0 : metric.averageDuration();
		double successRate = total == 0 ? 0.0 : (double) success * 100.0 / total;
		double failureRate = total == 0 ? 0.0 : (double) failed * 100.0 / total;
		double collaborationScore = collaborationScore(agentType);
		double humanApprovalRate = humanApprovalRate(agentType);
		AgentScore score = new AgentScore(agentType, total, round(successRate), avgDuration,
			round(failureRate), round(collaborationScore), round(humanApprovalRate));
		auditScore(agentType, score, metric);
		return score;
	}

	private AgentMetrics metricFor(String agentType) {
		if (metricsService == null) {
			return null;
		}
		return metricsService.listAgentMetrics().stream()
			.filter(metric -> agentType.equals(metric.agentName()))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Percentage of collaboration teams the agent joined (0-100). No teams
	 * yet means a neutral zero.
	 */
	private double collaborationScore(String agentType) {
		List<AgentTeam> teams = teamRepository == null ? List.of() : teamRepository.list();
		if (teams.isEmpty()) {
			return 0.0;
		}
		long joined = teams.stream()
			.filter(team -> team.getAgents().contains(agentType))
			.count();
		return joined * 100.0 / teams.size();
	}

	/**
	 * Percentage of the agent's requested human approvals that were granted
	 * (0-100). No approvals requested means a neutral zero.
	 */
	private double humanApprovalRate(String agentType) {
		List<HumanApproval> approvals = approvalRepository == null
			? List.of() : approvalRepository.list().stream()
				.filter(approval -> agentType.equals(approval.getRequester()))
				.toList();
		if (approvals.isEmpty()) {
			return 0.0;
		}
		long approved = approvals.stream()
			.filter(approval -> approval.getStatus() == HumanApprovalStatus.APPROVED)
			.count();
		return approved * 100.0 / approvals.size();
	}

	private void auditScore(String agentType, AgentScore score, AgentMetrics metric) {
		if (auditService == null) {
			return;
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("agentType", agentType);
		metadata.put("totalExecutions", score.totalExecutions());
		metadata.put("successRate", score.successRate());
		metadata.put("failureRate", score.failureRate());
		metadata.put("avgDuration", score.avgDuration());
		metadata.put("collaborationScore", score.collaborationScore());
		metadata.put("humanApprovalRate", score.humanApprovalRate());
		metadata.put("composite", score.composite());
		if (metric != null) {
			metadata.put("repairCount", metric.repairCount());
			metadata.put("changeCount", metric.changeCount());
		}
		if (traceService != null) {
			metadata.put("traceCount", traceService.listByAgent(agentType).size());
		}
		if (usageService != null) {
			metadata.put("totalTokens",
				usageService.getAgentUsage(agentType).totalTokens());
			metadata.put("estimatedCost",
				usageService.getAgentUsage(agentType).estimatedCost());
		}
		auditService.optimizationEvent(EventType.AGENT_SCORE_UPDATED, agentType, null, null,
			"AGENT_SCORE", "Agent score updated for " + agentType, Map.copyOf(metadata));
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
