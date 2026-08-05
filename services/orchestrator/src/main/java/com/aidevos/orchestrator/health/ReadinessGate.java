package com.aidevos.orchestrator.health;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Readiness gate for the operations phase. The application becomes ready only
 * after startup completes and, in PostgreSQL mode, after the V1..V7 schema
 * migrations have been fully applied. In-memory mode has no migration step and
 * only waits for startup.
 */
@Component
public class ReadinessGate {

	private final ObjectProvider<PostgresDocumentStore> documentStores;
	private volatile boolean startupComplete;

	public ReadinessGate(ObjectProvider<PostgresDocumentStore> documentStores) {
		this.documentStores = documentStores;
	}

	@EventListener(ApplicationReadyEvent.class)
	void markStartupComplete() {
		startupComplete = true;
	}

	public boolean isReady() {
		if (!startupComplete) {
			return false;
		}
		PostgresDocumentStore store = documentStores.getIfAvailable();
		return store == null || store.migrationsComplete();
	}

	public Map<String, Object> details() {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("startupComplete", startupComplete);
		PostgresDocumentStore store = documentStores.getIfAvailable();
		if (store != null) {
			details.put("migrations", store.migrationsComplete() ? "complete" : "pending");
		}
		return details;
	}
}
