package com.aidevos.orchestrator.orchestration;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * Graph-node agent abstraction: one implementation per AgentType
 * (Hermes/Codex/OpenClaw/TestAgent/RepairAgent). Implementations are thin
 * adapters that reuse the existing planner, executor, test and repair
 * services; they never duplicate business logic.
 */
public interface AgentExecutor {

	AgentType type();

	AgentExecutionResult execute(AgentExecutionContext context);
}
