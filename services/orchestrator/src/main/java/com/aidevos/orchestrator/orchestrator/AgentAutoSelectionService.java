package com.aidevos.orchestrator.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.optimization.AgentOptimizationService;
import com.aidevos.orchestrator.optimization.AgentScore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Autonomous agent selection for orchestrated tasks. The base flow follows
 * the task category (AgentSelector mapping); the selection is then refined
 * with the historical AgentScores (highest composite replaces the primary
 * execution agent), the memory context warnings (a repair agent joins the
 * flow) and the primary agent's historical success rate (a repair agent is
 * added when the rate is low). Every selection is audited as
 * AGENT_AUTO_SELECTED; nothing is applied to the execution chain directly.
 */
@Service
public class AgentAutoSelectionService {

	/** Success rate (percent) below which a repair agent is added. */
	static final double LOW_SUCCESS_RATE_THRESHOLD = 60.0;

	private final AgentSelector agentSelector;
	private final AgentOptimizationService agentOptimizationService;
	private final AuditService auditService;

	@Autowired
	public AgentAutoSelectionService(AgentSelector agentSelector,
			AgentOptimizationService agentOptimizationService, AuditService auditService) {
		this.agentSelector = agentSelector;
		this.agentOptimizationService = agentOptimizationService;
		this.auditService = auditService;
	}

	/**
	 * Selects the agent flow for a task: base flow by task category, then the
	 * score / memory / success-rate refinements. Each applied rule is audited
	 * with the taskId and the chosen agent.
	 */
	public List<AgentType> selectAgents(String taskId, String taskType,
			MemoryContext memoryContext) {
		String category = categoryOf(taskType);
		List<AgentType> flow = baseFlow(category);
		Map<AgentType, String> reasons = new LinkedHashMap<>();
		for (AgentType agent : flow) {
			reasons.put(agent, "base selection for " + category);
		}

		AgentScore best = bestScored();
		if (best != null && !flow.contains(agentOf(best.agentType()))) {
			AgentType primary = primaryExecutionAgent(flow);
			AgentType replacement = agentOf(best.agentType());
			flow = replace(flow, primary, replacement);
			if (replacement != null) {
				reasons.put(replacement, "highest composite agent score");
			}
		}

		if (hasWarnings(memoryContext) && !flow.contains(AgentType.REPAIR_AGENT)) {
			flow = insertRepairBeforeVerifier(flow);
			reasons.put(AgentType.REPAIR_AGENT, "memory context warnings");
		}

		AgentType primary = primaryExecutionAgent(flow);
		AgentScore primaryScore = scoreOf(primary);
		if (!flow.contains(AgentType.REPAIR_AGENT) && primaryScore != null
				&& primaryScore.totalExecutions() > 0
				&& primaryScore.successRate() < LOW_SUCCESS_RATE_THRESHOLD) {
			flow = insertRepairBeforeVerifier(flow);
			reasons.put(AgentType.REPAIR_AGENT,
				"low historical success rate of " + primary.name());
		}

		for (AgentType agent : flow) {
			audit(taskId, agent.name(),
				reasons.getOrDefault(agent, "autonomous selection"));
		}
		return flow;
	}

	public String categoryOf(String taskType) {
		String type = taskType == null || taskType.isBlank()
			? "GENERAL" : taskType.trim().toUpperCase();
		return switch (type) {
			case "BROWSER_TASK", "BROWSER_TEST", "BROWSER" -> "BROWSER_TASK";
			case "TEST_TASK", "TEST_VERIFY", "TESTING" -> "TEST_TASK";
			case "REPAIR_TASK", "REPAIR" -> "REPAIR_TASK";
			case "CODE_TASK", "CODE_GENERATION", "CODING" -> "CODE_TASK";
			default -> "CODE_TASK";
		};
	}

	/** Base agent flow for a category, before any refinement. */
	public List<AgentType> baseFlow(String category) {
		return switch (category) {
			case "BROWSER_TASK" -> new ArrayList<>(List.of(AgentType.HERMES,
				AgentType.OPENCLAW, AgentType.TEST_AGENT));
			case "TEST_TASK" -> new ArrayList<>(List.of(AgentType.TEST_AGENT));
			case "REPAIR_TASK" -> new ArrayList<>(List.of(AgentType.REPAIR_AGENT,
				AgentType.CODEX, AgentType.TEST_AGENT));
			default -> new ArrayList<>(List.of(AgentType.HERMES,
				AgentType.CODEX, AgentType.TEST_AGENT));
		};
	}

	private AgentScore bestScored() {
		if (agentOptimizationService == null) {
			return null;
		}
		return agentOptimizationService.scoreAllAgents().stream()
			.filter(score -> score.totalExecutions() > 0)
			.max(java.util.Comparator.comparingDouble(AgentScore::composite))
			.orElse(null);
	}

	private AgentScore scoreOf(AgentType agentType) {
		if (agentOptimizationService == null || agentType == null) {
			return null;
		}
		return agentOptimizationService.scoreAllAgents().stream()
			.filter(score -> score.agentType().equals(agentType.name()))
			.findFirst()
			.orElse(null);
	}

	/** The agent that performs the main work: the second node for flows of
	 * three or more, otherwise the only node. */
	private AgentType primaryExecutionAgent(List<AgentType> flow) {
		return flow.size() >= 3 ? flow.get(1) : flow.get(0);
	}

	private List<AgentType> replace(List<AgentType> flow, AgentType from, AgentType to) {
		if (from == null || to == null || from == to) {
			return flow;
		}
		List<AgentType> replaced = new ArrayList<>(flow);
		replaced.replaceAll(agent -> agent == from ? to : agent);
		return replaced;
	}

	private List<AgentType> insertRepairBeforeVerifier(List<AgentType> flow) {
		List<AgentType> extended = new ArrayList<>(flow);
		extended.add(Math.max(0, extended.size() - 1), AgentType.REPAIR_AGENT);
		return extended;
	}

	private boolean hasWarnings(MemoryContext memoryContext) {
		return memoryContext != null && !memoryContext.getWarnings().isEmpty();
	}

	private AgentType agentOf(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return AgentType.valueOf(name.trim().toUpperCase());
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private void audit(String taskId, String agentType, String reason) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("agentType", value(agentType));
		metadata.put("reason", value(reason));
		auditService.orchestratorEvent(EventType.AGENT_AUTO_SELECTED, taskId, taskId,
			null, null, "Agent auto-selected: " + value(agentType), Map.copyOf(metadata));
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
