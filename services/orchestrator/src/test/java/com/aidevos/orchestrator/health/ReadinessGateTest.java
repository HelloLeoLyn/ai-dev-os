package com.aidevos.orchestrator.health;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessGateTest {

	@Test
	void notReadyBeforeApplicationReadyEvent() {
		ReadinessGate gate = new ReadinessGate(emptyProvider());
		assertFalse(gate.isReady());
	}

	@Test
	void readyAfterStartupInInMemoryMode() {
		ReadinessGate gate = new ReadinessGate(emptyProvider());
		gate.markStartupComplete();
		assertTrue(gate.isReady());
	}

	@Test
	void postgresModeRequiresMigrationsBeforeReady() {
		PostgresDocumentStore store = mock(PostgresDocumentStore.class);
		when(store.migrationsComplete()).thenReturn(false);
		ReadinessGate gate = new ReadinessGate(provider(store));
		gate.markStartupComplete();
		assertFalse(gate.isReady());

		when(store.migrationsComplete()).thenReturn(true);
		assertTrue(gate.isReady());
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<PostgresDocumentStore> emptyProvider() {
		ObjectProvider<PostgresDocumentStore> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<PostgresDocumentStore> provider(PostgresDocumentStore store) {
		ObjectProvider<PostgresDocumentStore> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(store);
		return provider;
	}
}
