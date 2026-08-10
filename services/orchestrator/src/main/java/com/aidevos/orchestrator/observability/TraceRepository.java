package com.aidevos.orchestrator.observability;

import java.util.List;

/**
 * Persistence contract for traces. Implemented by the in-memory store; no
 * database migration is introduced.
 */
public interface TraceRepository {

	void save(TraceRecord trace);

	TraceRecord get(String traceId);

	List<TraceRecord> listByTask(String taskId);

	List<TraceRecord> listByProject(String projectId);

	List<TraceRecord> listByAgent(String agentType);
}
