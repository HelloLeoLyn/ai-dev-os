package com.aidevos.orchestrator.optimization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentCollaborationService;
import com.aidevos.orchestrator.collaboration.AgentTeam;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.observability.ExecutionTraceService;
import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.observability.TraceStatus;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Autonomous optimization and learning loop. On demand (analyzeTask /
 * analyzeSession) the service derives recommendations from the existing
 * traces, agent scores, collaboration team and memory, records them through
 * the in-memory optimization repository, audits OPTIMIZATION_STARTED /
 * OPTIMIZATION_RECOMMENDED / OPTIMIZATION_COMPLETED and writes an
 * AGENT_EXPERIENCE memory record with the successful/failed patterns and the
 * recommendation. Only suggestions are produced; neither the graph nor the
 * agent selection is modified automatically.
 */
@Service
public class OptimizationService {

	private static final Logger logger = LoggerFactory.getLogger(OptimizationService.class);

	/** Node duration (ms) above which a PERFORMANCE recommendation is made. */
	static final long SLOW_NODE_THRESHOLD_MS = 10_000;

	private final OptimizationRepository repository;
	private final AuditService auditService;
	private final MemoryService memoryService;
	private final TaskCenterService taskCenterService;
	private final AgentOptimizationService agentOptimizationService;
	private final ExecutionTraceService traceService;
	private final AgentRuntimeService runtimeService;
	private final AgentCollaborationService collaborationService;

	public OptimizationService(OptimizationRepository repository, AuditService auditService,
			MemoryService memoryService, TaskCenterService taskCenterService,
			AgentOptimizationService agentOptimizationService) {
		this(repository, auditService, memoryService, taskCenterService,
			agentOptimizationService, null, null, null);
	}

	@Autowired
	public OptimizationService(OptimizationRepository repository, AuditService auditService,
			MemoryService memoryService, TaskCenterService taskCenterService,
			AgentOptimizationService agentOptimizationService,
			ExecutionTraceService traceService, AgentRuntimeService runtimeService,
			AgentCollaborationService collaborationService) {
		this.repository = repository;
		this.auditService = auditService;
		this.memoryService = memoryService;
		this.taskCenterService = taskCenterService;
		this.agentOptimizationService = agentOptimizationService;
		this.traceService = traceService;
		this.runtimeService = runtimeService;
		this.collaborationService = collaborationService;
	}

	/**
	 * Runs the full learning loop for a task: OPTIMIZATION_STARTED, generates
	 * and records recommendations, writes the AGENT_EXPERIENCE memory and
	 * audits OPTIMIZATION_COMPLETED.
	 */
	public List<OptimizationRecord> analyzeTask(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			throw new IllegalArgumentException("Task id is required");
		}
		TaskRecord task = taskCenterService.getTask(taskId)
			.orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
		auditService.optimizationEvent(EventType.OPTIMIZATION_STARTED, null, taskId, null,
			null, "Optimization analysis started",
			Map.of("taskStatus", task.getStatus() == null ? "" : task.getStatus().name()));
		List<OptimizationRecord> records = generateRecommendations(taskId, null);
		writeExperience(task, records);
		auditService.optimizationEvent(EventType.OPTIMIZATION_COMPLETED, null, taskId, null,
			null, "Optimization analysis completed with " + records.size()
				+ " recommendations", Map.of("recommendationCount", records.size()));
		return records;
	}

	/**
	 * Runs the learning loop scoped to one runtime session. The session's
	 * task drives the analysis; recommendations carry the sessionId so the
	 * checkpoint trail stays traceable.
	 */
	public List<OptimizationRecord> analyzeSession(String sessionId) {
		if (runtimeService == null) {
			throw new IllegalStateException("Agent runtime is not available");
		}
		Optional<AgentSession> optional = runtimeService.getSession(sessionId);
		AgentSession session = optional.orElseThrow(() ->
			new IllegalArgumentException("Session not found: " + sessionId));
		auditService.optimizationEvent(EventType.OPTIMIZATION_STARTED, null,
			session.getTaskId(), sessionId, null, "Session optimization analysis started",
			Map.of("sessionStatus", session.getStatus() == null ? ""
				: session.getStatus().name()));
		List<OptimizationRecord> records = generateRecommendations(session.getTaskId(),
			sessionId);
		TaskRecord task = taskCenterService.getTask(session.getTaskId()).orElse(null);
		writeExperience(task, records);
		auditService.optimizationEvent(EventType.OPTIMIZATION_COMPLETED, null,
			session.getTaskId(), sessionId, null,
			"Session optimization analysis completed with " + records.size()
				+ " recommendations", Map.of("recommendationCount", records.size()));
		return records;
	}

	/**
	 * Generates recommendations for a task (optionally scoped to a session)
	 * from the existing traces, agent scores and collaboration data. Every
	 * recommendation is recorded and audited as OPTIMIZATION_RECOMMENDED.
	 */
	public List<OptimizationRecord> generateRecommendations(String taskId, String sessionId) {
		List<OptimizationRecord> recommendations = new ArrayList<>();
		List<GraphOptimizationSuggestion> suggestions = suggestGraphOptimizations(taskId);
		long failedTraces = suggestions.stream()
			.filter(suggestion -> GraphOptimizationSuggestion.AGENT_REPLACEMENT
				.equals(suggestion.type()))
			.count();
		if (failedTraces > 0) {
			recommendations.add(recordOptimization(taskId, sessionId,
				OptimizationType.FAILURE_PATTERN,
				failedTraces + " failed node(s) detected; route a repair agent or retry "
					+ "with the recommended agent before continuing",
				failureConfidence(failedTraces)));
		}
		AgentScore best = bestAgent();
		if (best != null) {
			recommendations.add(recordOptimization(taskId, sessionId,
				OptimizationType.AGENT_SELECTION,
				"Prefer agent " + best.agentType() + " (success rate "
					+ Math.round(best.successRate()) + "%) for future executions of this task",
				Math.max(0.1, best.successRate() / 100.0)));
		}
		boolean toolSuggestion = suggestions.stream()
			.anyMatch(suggestion -> GraphOptimizationSuggestion.TOOL_REPLACEMENT
				.equals(suggestion.type()));
		if (toolSuggestion) {
			recommendations.add(recordOptimization(taskId, sessionId,
				OptimizationType.TOOL_USAGE,
				"No tool usage recorded; enable MCP tools for the task to reduce "
					+ "agent round-trips", 0.5));
		}
		suggestions.stream()
			.filter(suggestion -> GraphOptimizationSuggestion.ORDER
				.equals(suggestion.type()))
			.findFirst()
			.ifPresent(suggestion -> recommendations.add(recordOptimization(taskId, sessionId,
				OptimizationType.PERFORMANCE, suggestion.reason(), suggestion.confidence())));
		suggestions.stream()
			.filter(suggestion -> GraphOptimizationSuggestion.AGENT_REPLACEMENT
				.equals(suggestion.type()))
			.findFirst()
			.ifPresent(suggestion -> recommendations.add(recordOptimization(taskId, sessionId,
				OptimizationType.GRAPH_FLOW,
				"Consider replacing " + value(suggestion.currentAgent())
					+ " with " + value(suggestion.recommendedAgent()) + " at node "
					+ value(suggestion.nodeId()), suggestion.confidence())));
		return recommendations;
	}

	/**
	 * Read-only graph improvement suggestions for a task: failed nodes get an
	 * AGENT_REPLACEMENT suggestion, slow nodes an ORDER suggestion and
	 * missing tool usage a TOOL_REPLACEMENT suggestion. Suggestions are
	 * never applied automatically.
	 */
	public List<GraphOptimizationSuggestion> suggestGraphOptimizations(String taskId) {
		List<GraphOptimizationSuggestion> suggestions = new ArrayList<>();
		List<TraceRecord> traces = traceService == null || taskId == null
			? List.of() : traceService.listByTask(taskId);
		AgentScore best = bestAgent();
		TraceRecord slowest = null;
		for (TraceRecord trace : traces) {
			if (trace.getStatus() == TraceStatus.FAILED) {
				String failedAgent = value(trace.getAgentType());
				suggestions.add(new GraphOptimizationSuggestion(
					GraphOptimizationSuggestion.AGENT_REPLACEMENT,
					value(trace.getNodeId()), failedAgent,
					best == null || best.agentType().equals(failedAgent)
						? null : best.agentType(),
					null, null,
					"Node executed by " + failedAgent + " failed",
					failureConfidence(1)));
			}
			if (trace.getDuration() > SLOW_NODE_THRESHOLD_MS
					&& (slowest == null || trace.getDuration() > slowest.getDuration())) {
				slowest = trace;
			}
		}
		if (slowest != null) {
			suggestions.add(new GraphOptimizationSuggestion(
				GraphOptimizationSuggestion.ORDER, value(slowest.getNodeId()),
				value(slowest.getAgentType()), null, null, null,
				"Node took " + slowest.getDuration() + "ms; consider splitting it or "
					+ "moving it later in the flow", 0.4));
		}
		boolean anyTool = traces.stream().anyMatch(trace -> trace.getToolId() != null);
		if (!traces.isEmpty() && !anyTool) {
			suggestions.add(new GraphOptimizationSuggestion(
				GraphOptimizationSuggestion.TOOL_REPLACEMENT, null, null, null, null,
				"mcp-router", "No tool usage recorded for the task", 0.5));
		}
		return suggestions;
	}

	/**
	 * Stores an optimization recommendation and audits OPTIMIZATION_RECOMMENDED
	 * with the taskId, sessionId and type metadata.
	 */
	public OptimizationRecord recordOptimization(String taskId, String sessionId,
			OptimizationType type, String recommendation, double confidence) {
		OptimizationRecord record = new OptimizationRecord("optimization-" + UUID.randomUUID(),
			taskId, sessionId, type, recommendation, confidence, Instant.now());
		repository.save(record);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("optimizationId", record.getId());
		if (sessionId != null) {
			metadata.put("sessionId", sessionId);
		}
		metadata.put("confidence", confidence);
		auditService.optimizationEvent(EventType.OPTIMIZATION_RECOMMENDED, record.getId(),
			taskId, sessionId, type == null ? null : type.name(), recommendation,
			Map.copyOf(metadata));
		return record;
	}

	public List<OptimizationRecord> getRecommendations(String taskId) {
		return taskId == null || taskId.isBlank() ? List.of()
			: repository.listByTask(taskId);
	}

	public List<OptimizationRecord> getAllRecommendations() {
		return repository.list();
	}

	public Optional<OptimizationRecord> getRecommendation(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(repository.get(id));
	}

	/**
	 * Best ranked agent according to the composite score, used as the
	 * AGENT_SELECTION recommendation target. Null when no agent was scored.
	 */
	public AgentScore bestAgent() {
		List<AgentScore> scores = agentOptimizationService == null
			? List.of() : agentOptimizationService.scoreAllAgents();
		return scores.isEmpty() ? null : scores.get(0);
	}

	private void writeExperience(TaskRecord task, List<OptimizationRecord> records) {
		if (memoryService == null || records.isEmpty()) {
			return;
		}
		try {
			MemoryRecord record = new MemoryRecord();
			record.setProjectId(task == null ? "default" : task.getProjectId());
			record.setType(MemoryType.AGENT_EXPERIENCE);
			record.setKey("agent-experience:optimization:" + (task == null
				? "task-unknown" : task.getTaskId()));
			record.setContent(experienceContent(task, records));
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the optimization loop; already audited.
			logger.warn("Failed to persist optimization experience for task {}",
				task == null ? null : task.getTaskId(), exception);
		}
	}

	private String experienceContent(TaskRecord task, List<OptimizationRecord> records) {
		StringBuilder builder = new StringBuilder();
		builder.append("任务: ").append(task == null ? "task-unknown" : task.getTaskId())
			.append(System.lineSeparator());
		List<String> failures = records.stream()
			.filter(record -> record.getType() == OptimizationType.FAILURE_PATTERN)
			.map(OptimizationRecord::getRecommendation)
			.toList();
		List<String> successes = records.stream()
			.filter(record -> record.getType() == OptimizationType.PERFORMANCE)
			.map(OptimizationRecord::getRecommendation)
			.toList();
		builder.append("failedPattern: ").append(failures.isEmpty()
			? "none" : String.join("; ", failures)).append(System.lineSeparator());
		builder.append("successfulPattern: ").append(successes.isEmpty()
			? "none" : String.join("; ", successes)).append(System.lineSeparator());
		builder.append("recommendation:").append(System.lineSeparator());
		for (OptimizationRecord record : records) {
			builder.append("  ").append(record.getType().name()).append(": ")
				.append(record.getRecommendation()).append(System.lineSeparator());
		}
		return builder.toString();
	}

	private double failureConfidence(long failed) {
		return failed <= 0 ? 0.0 : Math.min(0.95, 0.4 + failed * 0.1);
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
