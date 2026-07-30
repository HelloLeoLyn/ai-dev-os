package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;

public interface AgentExecutor {

	String getType();

	ExecutionResult execute(TaskDefinition taskDefinition);
}
