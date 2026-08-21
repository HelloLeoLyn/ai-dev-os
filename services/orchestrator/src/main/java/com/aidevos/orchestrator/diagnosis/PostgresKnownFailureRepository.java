package com.aidevos.orchestrator.diagnosis;

import java.util.List;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * KnownFailure PostgreSQL 实现：复用 PostgresDocumentStore（repository_type=known-failure，
 * 无新 migration）。KnownFailure 为 record，可直接 Jackson 序列化/反序列化。
 * 条件装配与 InMemoryKnownFailureRepository 互斥（havingValue=postgresql）。
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
final class PostgresKnownFailureRepository implements KnownFailureRepository {

	private static final String TYPE = "known-failure";

	private final PostgresDocumentStore store;

	PostgresKnownFailureRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(KnownFailure failure) {
		store.put(TYPE, failure.fingerprint(), failure, failure.code());
	}

	@Override
	public KnownFailure get(String fingerprint) {
		return store.get(TYPE, fingerprint, KnownFailure.class);
	}

	@Override
	public List<KnownFailure> list() {
		return store.all(TYPE, KnownFailure.class);
	}
}
