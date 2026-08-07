package com.aidevos.orchestrator.agentcapability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.modelrouter.TaskType;
import org.springframework.stereotype.Component;

/**
 * Matches a TaskType to an agent capability and dynamically selects the best
 * registered agent for that capability from the AgentRegistry (AgentManager).
 * When several agents provide the same capability the selection prefers:
 * 1. enabled agents, 2. the highest version, 3. the most recently updated
 * agent. No fixed agent name is hard-coded.
 */
@Component
public class AgentCapabilityResolver {

	private final AgentManager agentManager;

	public AgentCapabilityResolver(AgentManager agentManager) {
		this.agentManager = agentManager;
	}

	/**
	 * Maps a task type to the capability responsible for it.
	 */
	public String capabilityFor(TaskType taskType) {
		return switch (taskType == null ? TaskType.GENERAL : taskType) {
			case TASK_ANALYSIS -> AgentCapability.PLANNING;
			case CODE_GENERATION -> AgentCapability.CODING;
			case BROWSER_TEST -> AgentCapability.BROWSER;
			case TEST_VERIFY -> AgentCapability.TESTING;
			default -> AgentCapability.PLANNING;
		};
	}

	/**
	 * Resolves the best registered agent for the capability of a task type.
	 */
	public Optional<AgentDefinition> resolveAgent(TaskType taskType) {
		return resolveAgent(capabilityFor(taskType));
	}

	/**
	 * Resolves the best registered agent providing the given capability.
	 */
	public Optional<AgentDefinition> resolveAgent(String capability) {
		return candidates(capability).stream().max(priority());
	}

	/**
	 * Lists all registered agents providing the capability, best first.
	 */
	public List<AgentDefinition> resolveByCapability(String capability) {
		List<AgentDefinition> agents = new ArrayList<>(candidates(capability));
		agents.sort(priority().reversed());
		return List.copyOf(agents);
	}

	public List<AgentCapability> listCapabilities() {
		return AgentCapability.presets();
	}

	private List<AgentDefinition> candidates(String capability) {
		if (capability == null || capability.isBlank()) {
			return List.of();
		}
		return agentManager.getAllAgents().stream()
			.filter(agent -> hasCapability(agent, capability))
			.toList();
	}

	private boolean hasCapability(AgentDefinition agent, String capability) {
		List<String> capabilities = agent.getCapabilities();
		return capabilities != null && capabilities.contains(capability);
	}

	private Comparator<AgentDefinition> priority() {
		return Comparator
			.comparing(AgentDefinition::isEnabled)
			.thenComparing(AgentDefinition::getVersion, AgentCapabilityResolver::compareVersions)
			.thenComparing(AgentDefinition::getUpdatedAt,
				Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(AgentDefinition::getName);
	}

	private static int compareVersions(String left, String right) {
		int[] leftSegments = segments(left);
		int[] rightSegments = segments(right);
		int length = Math.max(leftSegments.length, rightSegments.length);
		for (int i = 0; i < length; i++) {
			int leftValue = i < leftSegments.length ? leftSegments[i] : 0;
			int rightValue = i < rightSegments.length ? rightSegments[i] : 0;
			if (leftValue != rightValue) {
				return Integer.compare(leftValue, rightValue);
			}
		}
		return 0;
	}

	private static int[] segments(String version) {
		if (version == null || version.isBlank()) {
			return new int[0];
		}
		String[] parts = version.trim().split("\\.");
		int[] result = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			result[i] = leadingNumber(parts[i]);
		}
		return result;
	}

	private static int leadingNumber(String part) {
		StringBuilder digits = new StringBuilder();
		for (char character : part.toCharArray()) {
			if (Character.isDigit(character)) {
				digits.append(character);
			}
			else {
				break;
			}
		}
		if (digits.isEmpty()) {
			return 0;
		}
		try {
			return Integer.parseInt(digits.toString());
		}
		catch (NumberFormatException exception) {
			return 0;
		}
	}
}
