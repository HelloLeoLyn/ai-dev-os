package com.aidevos.orchestrator.execution.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Registry of deterministic system actions for SYSTEM_STEP. An unknown action
 * name or an action without a registered executor throws, so the scheduler
 * fails the step closed instead of falling back to an LLM.
 */
@Service
public class SystemStepService {

	private final Map<SystemAction, SystemActionExecutor> executors;

	public SystemStepService(List<SystemActionExecutor> executors) {
		Map<SystemAction, SystemActionExecutor> byAction = new LinkedHashMap<>();
		if (executors != null) {
			for (SystemActionExecutor executor : executors) {
				if (executor != null && executor.action() != null) {
					byAction.put(executor.action(), executor);
				}
			}
		}
		this.executors = Map.copyOf(byAction);
	}

	/**
	 * Executes the declared system action. Fails closed for unknown action
	 * names and for actions with no registered executor.
	 */
	public SystemActionResult execute(String actionName, SystemActionContext context) {
		SystemAction action = SystemAction.fromName(actionName)
			.orElseThrow(() -> new IllegalStateException(
				"Unsupported system action: " + actionName));
		SystemActionExecutor executor = executors.get(action);
		if (executor == null) {
			throw new IllegalStateException(
				"No executor registered for system action: " + action.name());
		}
		return executor.execute(context);
	}
}
