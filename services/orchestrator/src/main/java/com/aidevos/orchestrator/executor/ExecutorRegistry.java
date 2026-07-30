package com.aidevos.orchestrator.executor;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExecutorRegistry {

	private final Map<String, AgentExecutor> executors = new HashMap<>();

	public ExecutorRegistry(List<AgentExecutor> executors) {
		executors.forEach(executor -> register(executor.getType(), executor));
	}

	public void register(String type, AgentExecutor executor) {
		if (executors.putIfAbsent(type, executor) != null) {
			throw new IllegalStateException("Executor type already registered: " + type);
		}
	}

	public AgentExecutor get(String type) {
		return executors.get(type);
	}
}
