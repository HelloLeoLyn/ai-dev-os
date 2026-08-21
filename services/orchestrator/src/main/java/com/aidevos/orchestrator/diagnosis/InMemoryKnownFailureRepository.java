package com.aidevos.orchestrator.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory KnownFailure 实现。
 * 条件装配与 Postgres 版互斥（避免 bean 冲突）：
 * in-memory / matchIfMissing → 本实现；postgresql → PostgresKnownFailureRepository。
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type",
	havingValue = "in-memory", matchIfMissing = true)
public class InMemoryKnownFailureRepository implements KnownFailureRepository {

	private final Map<String, KnownFailure> failures = new java.util.LinkedHashMap<>();

	@Override
	public synchronized void save(KnownFailure failure) {
		failures.put(failure.fingerprint(), failure);
	}

	@Override
	public synchronized KnownFailure get(String fingerprint) {
		return failures.get(fingerprint);
	}

	@Override
	public synchronized List<KnownFailure> list() {
		return new ArrayList<>(failures.values());
	}
}
