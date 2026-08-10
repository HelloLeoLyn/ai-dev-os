package com.aidevos.orchestrator.memory.search;

/**
 * A memory retrieval query: the free-text query, optional task / agent type
 * filters, the project scope and a result limit.
 */
public record MemoryQuery(
		String query,
		String taskType,
		String agentType,
		String projectId,
		int limit) {

	public MemoryQuery {
		limit = limit <= 0 ? 10 : limit;
	}
}
