package com.aidevos.orchestrator.tool.mcp;

import java.time.Duration;
import java.util.Map;

import tools.jackson.databind.JsonNode;

public interface McpSession extends AutoCloseable {

	void connect();

	JsonNode request(String method, Map<String, Object> parameters, Duration timeout);

	void notify(String method, Map<String, Object> parameters);

	boolean isConnected();

	@Override
	void close();
}
