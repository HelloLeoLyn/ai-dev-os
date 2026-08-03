package com.aidevos.orchestrator.audit;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryAuditRepository implements AuditRepository {
	private static final Comparator<EventRecord> ORDER = Comparator
		.comparing(EventRecord::occurredAt).thenComparingLong(EventRecord::sequence)
		.thenComparing(EventRecord::id);
	private final Map<String, EventRecord> events = new LinkedHashMap<>();
	private final Map<String, String> byIdempotencyKey = new LinkedHashMap<>();
	private long nextSequence = 1;

	@Override
	public synchronized EventRecord append(EventRecord event) {
		String existingId = byIdempotencyKey.get(event.idempotencyKey());
		if (existingId != null) return events.get(existingId);
		if (events.containsKey(event.id())) {
			throw new IllegalStateException("Audit event id already exists: " + event.id());
		}
		EventRecord stored = event.withSequence(nextSequence++);
		events.put(stored.id(), stored);
		byIdempotencyKey.put(stored.idempotencyKey(), stored.id());
		return stored;
	}

	@Override
	public synchronized EventRecord get(String id) { return events.get(id); }

	@Override
	public synchronized List<EventRecord> query(EventQuery query) {
		EventQuery effective = query == null ? EventQuery.all() : query;
		return events.values().stream().filter(effective::matches).sorted(ORDER)
			.skip(effective.offset()).limit(effective.limit()).toList();
	}

	@Override
	public synchronized long count(EventQuery query) {
		EventQuery effective = query == null ? EventQuery.all() : query;
		return events.values().stream().filter(effective::matches).count();
	}
}
