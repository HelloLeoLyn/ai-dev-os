package com.aidevos.orchestrator.outbox;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;

class InMemoryOutboxRepositoryTest extends OutboxRepositoryContract {

	private final Instant now = Instant.now().plusSeconds(60);
	private InMemoryOutboxRepository repository;

	@BeforeEach
	void setUp() {
		repository = new InMemoryOutboxRepository();
	}

	@Override
	OutboxRepository repository() { return repository; }

	@Override
	Instant now() { return now; }
}
