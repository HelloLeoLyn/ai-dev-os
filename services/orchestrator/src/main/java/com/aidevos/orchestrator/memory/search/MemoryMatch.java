package com.aidevos.orchestrator.memory.search;

import java.util.Map;

import com.aidevos.orchestrator.memory.MemoryType;

/**
 * One matched memory record with its relevance score, summary and (when
 * available) the recorded solution.
 */
public record MemoryMatch(
		String memoryId,
		MemoryType type,
		double score,
		String summary,
		String solution,
		Map<String, Object> metadata) {

	public MemoryMatch {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
