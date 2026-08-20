package com.aidevos.orchestrator.execution.system;

/**
 * Executes one allowlisted system action without an LLM. Executors are
 * registered by their {@link #action()} in {@link SystemStepService}; the
 * scheduler never dispatches a SYSTEM_STEP to an agent or tool executor.
 */
public interface SystemActionExecutor {

	SystemAction action();

	SystemActionResult execute(SystemActionContext context);
}
