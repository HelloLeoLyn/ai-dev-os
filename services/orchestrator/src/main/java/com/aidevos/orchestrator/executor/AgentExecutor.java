package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;

public interface AgentExecutor {

	ExecutionResult execute(TaskDefinition taskDefinition);
}
