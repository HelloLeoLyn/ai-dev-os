package com.aidevos.orchestrator.memory;

/**
 * Request body for creating a memory record.
 */
public record CreateMemoryRequest(
		String projectId,
		MemoryType type,
		String key,
		String content) {
}
