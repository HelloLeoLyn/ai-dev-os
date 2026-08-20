package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;
import com.aidevos.orchestrator.execution.ExecutionStateRepository;
import com.aidevos.orchestrator.execution.RunExecutionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
class PostgresExecutionStateRepository implements ExecutionStateRepository {
	private static final String TYPE = "execution-state";
	private final PostgresDocumentStore store;
	PostgresExecutionStateRepository(PostgresDocumentStore store) {
		this.store = store;
	}
	public void save(RunExecutionState value) {
		store.put(TYPE, value.getRunId(), PersistenceSnapshots.ExecutionState.of(value),
			value.getRunId());
	}
	public RunExecutionState get(String id) {
		PersistenceSnapshots.ExecutionState snapshot =
			store.get(TYPE, id, PersistenceSnapshots.ExecutionState.class);
		return snapshot == null ? null : snapshot.value();
	}
	public List<RunExecutionState> getAll() {
		return store.all(TYPE, PersistenceSnapshots.ExecutionState.class).stream()
			.map(PersistenceSnapshots.ExecutionState::value).toList();
	}
}
