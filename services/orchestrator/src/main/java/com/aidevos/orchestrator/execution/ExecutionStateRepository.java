package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.persistence.CrudRepository;

/**
 * Persistence contract for per-run execution counters and intervention state.
 * Implemented by the in-memory store and the PostgreSQL document store.
 */
public interface ExecutionStateRepository extends CrudRepository<RunExecutionState> {
}
