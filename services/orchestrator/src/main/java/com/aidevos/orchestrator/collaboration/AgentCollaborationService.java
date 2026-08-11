package com.aidevos.orchestrator.collaboration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Multi-agent collaboration inside a runtime session: a team of agents that
 * exchange messages and hand off execution context as the graph runs. Teams
 * are created by the execution graph executor around a session; each graph
 * node joins the team, handoffs carry the project memory context (similar
 * tasks / solutions / warnings) to the receiving agent, and a completed team
 * writes an AGENT_EXPERIENCE memory record. State stays in the in-memory
 * team and message repositories; audit and memory reuse the existing
 * AuditService and MemoryService.
 */
@Service
public class AgentCollaborationService {

	private static final Logger logger = LoggerFactory.getLogger(AgentCollaborationService.class);

	private final AgentTeamRepository teamRepository;
	private final AgentMessageRepository messageRepository;
	private final AuditService auditService;
	private final MemoryService memoryService;
	private final TaskCenterService taskCenterService;

	@Autowired
	public AgentCollaborationService(AgentTeamRepository teamRepository,
			AgentMessageRepository messageRepository, AuditService auditService,
			MemoryService memoryService, TaskCenterService taskCenterService) {
		this.teamRepository = teamRepository;
		this.messageRepository = messageRepository;
		this.auditService = auditService;
		this.memoryService = memoryService;
		this.taskCenterService = taskCenterService;
	}

	/**
	 * Creates the collaboration team for a task and runtime session. When a
	 * team already exists for the session (e.g. a resumed session), that
	 * team is returned so recovery reuses the same team.
	 */
	public AgentTeam createTeam(String taskId, String sessionId) {
		for (AgentTeam existing : teamRepository.listByTask(taskId)) {
			if (sessionId != null && sessionId.equals(existing.getSessionId())) {
				return existing;
			}
		}
		AgentTeam team = new AgentTeam("team-" + UUID.randomUUID(), taskId, sessionId);
		teamRepository.save(team);
		team.markRunning();
		teamRepository.save(team);
		collaborationEvent(EventType.AGENT_TEAM_CREATED, team, null, null, null,
			AgentTeamStatus.CREATED.name(), AgentTeamStatus.RUNNING.name(),
			"Agent team created", Map.of());
		return team;
	}

	/**
	 * Adds an agent to the team and marks the team RUNNING (an agent is
	 * executing or about to execute). The join is audited.
	 */
	public AgentTeam addAgent(String teamId, String agentType) {
		AgentTeam team = require(teamId);
		if (isTerminal(team.getStatus())) {
			throw new IllegalStateException("Team already finished: " + teamId);
		}
		boolean joined = !team.getAgents().contains(agentType);
		team.addAgent(agentType);
		if (team.getStatus() != AgentTeamStatus.RUNNING) {
			team.markRunning();
		}
		teamRepository.save(team);
		if (joined) {
			collaborationEvent(EventType.AGENT_JOINED_TEAM, team, agentType, null, null,
				AgentTeamStatus.RUNNING.name(), AgentTeamStatus.RUNNING.name(),
				"Agent joined team: " + agentType, Map.of("agentType", value(agentType)));
		}
		return team;
	}

	/**
	 * Records a message inside the team. The message is stored, audited with
	 * the team/from/to/messageType metadata and returned. Messages are
	 * append-only records, so they can be delivered even after the team
	 * finished (e.g. human feedback added after the collaboration completed).
	 */
	public AgentMessage sendMessage(String teamId, AgentMessage message) {
		AgentTeam team = require(teamId);
		messageRepository.save(message);
		collaborationEvent(EventType.AGENT_MESSAGE_SENT, team, message.getFromAgent(),
			message.getToAgent(), message.getMessageType(), team.getStatus().name(),
			team.getStatus().name(), "Message sent: " + message.getMessageType().name(),
			Map.of("messageId", message.getMessageId()));
		return message;
	}

	/**
	 * Records a message inside the team, generating the message id and
	 * timestamp. The message is stored and audited.
	 */
	public AgentMessage sendMessage(String teamId, String fromAgent, String toAgent,
			AgentMessageType messageType, String content) {
		AgentMessageType type = messageType == null ? AgentMessageType.REQUEST : messageType;
		return sendMessage(teamId, new AgentMessage("msg-" + UUID.randomUUID(), teamId,
			fromAgent, toAgent, type, content, Instant.now()));
	}

	/**
	 * Hands execution from one agent to the next. The project memory context
	 * (similar tasks / solutions / warnings) is read and passed along in the
	 * handoff message so the receiving agent continues with the historical
	 * experience; the team is marked WAITING until the next agent runs.
	 */
	public AgentMessage handoff(String teamId, String from, String to,
			MemoryContext memoryContext) {
		AgentTeam team = require(teamId);
		if (isTerminal(team.getStatus())) {
			throw new IllegalStateException("Team already finished: " + teamId);
		}
		team.markWaiting();
		teamRepository.save(team);
		String content = memoryContent(memoryContext);
		AgentMessage message = new AgentMessage("msg-" + UUID.randomUUID(), teamId,
			from, to, AgentMessageType.HANDOFF, content, Instant.now());
		messageRepository.save(message);
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("messageId", message.getMessageId());
		metadata.put("memory", memorySummary(memoryContext));
		collaborationEvent(EventType.AGENT_HANDOFF, team, from, to,
			AgentMessageType.HANDOFF, AgentTeamStatus.RUNNING.name(),
			AgentTeamStatus.WAITING.name(),
			"Handoff: " + value(from) + " -> " + value(to), metadata);
		return message;
	}

	/**
	 * Completes the team: marks it COMPLETED, audits the completion and
	 * writes the collaboration outcome as an AGENT_EXPERIENCE memory record
	 * so later tasks can reuse the team's solutions and warnings.
	 */
	public AgentTeam completeTeam(String teamId) {
		AgentTeam team = require(teamId);
		if (isTerminal(team.getStatus())) {
			return team;
		}
		String from = team.getStatus().name();
		team.markCompleted();
		teamRepository.save(team);
		collaborationEvent(EventType.AGENT_COLLABORATION_COMPLETED, team, null, null, null,
			from, AgentTeamStatus.COMPLETED.name(), "Agent collaboration completed",
			Map.of("agents", team.getAgents()));
		saveAgentExperience(team);
		return team;
	}

	/**
	 * Marks the team FAILED and audits the failure. Used when the execution
	 * graph stops on a node failure.
	 */
	public AgentTeam failTeam(String teamId, String error) {
		AgentTeam team = require(teamId);
		if (team.getStatus() == AgentTeamStatus.COMPLETED
			|| team.getStatus() == AgentTeamStatus.FAILED) {
			return team;
		}
		String from = team.getStatus().name();
		team.markFailed();
		teamRepository.save(team);
		collaborationEvent(EventType.AGENT_COLLABORATION_FAILED, team, null, null, null,
			from, AgentTeamStatus.FAILED.name(),
			"Agent collaboration failed: " + value(error),
			Map.of("error", value(error)));
		return team;
	}

	public Optional<AgentTeam> getTeam(String teamId) {
		if (teamId == null || teamId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(teamRepository.get(teamId));
	}

	/**
	 * The most recent team for a task, used by observability to attach the
	 * collaboration view to the task bundle.
	 */
	public Optional<AgentTeam> teamForTask(String taskId) {
		List<AgentTeam> teams = teamRepository.listByTask(taskId);
		return teams.isEmpty() ? Optional.empty() : Optional.of(teams.get(teams.size() - 1));
	}

	public List<AgentMessage> messages(String teamId) {
		return messageRepository.listByTeam(teamId);
	}

	/**
	 * Human-readable handoff trail ("FROM->TO") for observability, in the
	 * order the handoffs happened.
	 */
	public List<String> handoffs(String teamId) {
		return messageRepository.listByTeam(teamId).stream()
			.filter(message -> message.getMessageType() == AgentMessageType.HANDOFF)
			.map(message -> value(message.getFromAgent()) + "->"
				+ value(message.getToAgent()))
			.toList();
	}

	private AgentTeam require(String teamId) {
		if (teamId == null || teamId.isBlank()) {
			throw new IllegalArgumentException("Team id is required");
		}
		AgentTeam team = teamRepository.get(teamId);
		if (team == null) {
			throw new IllegalArgumentException("Team not found: " + teamId);
		}
		return team;
	}

	private boolean isTerminal(AgentTeamStatus status) {
		return status == AgentTeamStatus.COMPLETED || status == AgentTeamStatus.FAILED;
	}

	private void collaborationEvent(EventType type, AgentTeam team, String fromAgent,
			String toAgent, AgentMessageType messageType, String fromStatus, String toStatus,
			String summary, Map<String, Object> metadata) {
		Map<String, Object> enriched = new LinkedHashMap<>();
		enriched.put("teamId", value(team.getTeamId()));
		if (fromAgent != null) {
			enriched.put("fromAgent", fromAgent);
		}
		if (toAgent != null) {
			enriched.put("toAgent", toAgent);
		}
		if (messageType != null) {
			enriched.put("messageType", messageType.name());
		}
		if (metadata != null) {
			enriched.putAll(metadata);
		}
		auditService.collaborationEvent(type, team.getTeamId(), team.getTaskId(),
			team.getSessionId(), fromAgent, toAgent, messageType, fromStatus, toStatus,
			summary, Map.copyOf(enriched));
	}

	private String memoryContent(MemoryContext memoryContext) {
		if (memoryContext == null || memoryContext.isEmpty()) {
			return "Handoff (no memory context)";
		}
		StringBuilder builder = new StringBuilder("Handoff with memory context");
		if (!memoryContext.getSimilarTasks().isEmpty()) {
			builder.append(System.lineSeparator())
				.append("similarTasks: ").append(memoryContext.getSimilarTasks().size());
		}
		if (!memoryContext.getSolutions().isEmpty()) {
			builder.append(System.lineSeparator())
				.append("solutions: ").append(memoryContext.getSolutions().size());
		}
		if (!memoryContext.getWarnings().isEmpty()) {
			builder.append(System.lineSeparator())
				.append("warnings: ").append(String.join("; ", memoryContext.getWarnings()));
		}
		return builder.toString();
	}

	private Map<String, Object> memorySummary(MemoryContext memoryContext) {
		Map<String, Object> summary = new LinkedHashMap<>();
		if (memoryContext == null) {
			return summary;
		}
		summary.put("similarTasks", memoryContext.getSimilarTasks().stream()
			.map(match -> match.memoryId()).toList());
		summary.put("solutions", memoryContext.getSolutions().stream()
			.map(match -> match.memoryId()).toList());
		summary.put("warnings", List.copyOf(memoryContext.getWarnings()));
		return summary;
	}

	private void saveAgentExperience(AgentTeam team) {
		try {
			TaskRecord task = taskCenterService.getTask(team.getTaskId()).orElse(null);
			MemoryRecord record = new MemoryRecord();
			record.setProjectId(task == null ? "default" : task.getProjectId());
			record.setType(MemoryType.AGENT_EXPERIENCE);
			record.setKey("agent-experience:team:" + team.getTeamId());
			record.setContent(experienceContent(team));
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the collaboration flow; already audited.
			logger.warn("Failed to persist agent experience for team {}",
				team.getTeamId(), exception);
		}
	}

	private String experienceContent(AgentTeam team) {
		StringBuilder builder = new StringBuilder();
		builder.append("任务: ").append(team.getTaskId()).append(System.lineSeparator())
			.append("会话: ").append(value(team.getSessionId())).append(System.lineSeparator())
			.append("团队: ").append(team.getTeamId()).append(System.lineSeparator())
			.append("Agent: ").append(String.join(", ", team.getAgents()))
			.append(System.lineSeparator())
			.append("交接:").append(System.lineSeparator());
		for (String handoff : handoffs(team.getTeamId())) {
			builder.append("  ").append(handoff).append(System.lineSeparator());
		}
		return builder.toString();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
