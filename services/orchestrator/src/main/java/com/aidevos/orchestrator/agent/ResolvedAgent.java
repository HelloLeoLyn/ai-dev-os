package com.aidevos.orchestrator.agent;

import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;

public record ResolvedAgent(AgentDefinition definition, AgentExecutor executor) {
}
