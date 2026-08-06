package com.aidevos.orchestrator.memory;

import java.util.List;

/**
 * Persistence contract for memory records. Implemented by the in-memory store
 * and by the PostgreSQL-backed repository.
 */
public interface MemoryRepository {

	void save(MemoryRecord record);

	MemoryRecord get(String id);

	List<MemoryRecord> list(String projectId, MemoryType type);

	boolean delete(String id);
}
