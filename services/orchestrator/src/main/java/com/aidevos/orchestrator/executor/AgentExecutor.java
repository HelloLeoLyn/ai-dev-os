package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;

public interface AgentExecutor {

	String getType();

	ExecutionResult execute(ExecutionContext context);
}
