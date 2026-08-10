package com.aidevos.orchestrator.security;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.security.secret.SecretManager;
import com.aidevos.orchestrator.security.secret.SecretRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-A: secret storage, masking and access control. Raw values never
 * leave the manager and agent reads are denied by default.
 */
class SecretManagerTest {

	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private SecretManager manager;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		manager = new SecretManager(auditService);
	}

	@Test
	void shouldStoreAndMaskSecrets() {
		manager.put("github-token", "ghp_super-secret-value");

		SecretRecord record = manager.getRecord("github-token").orElseThrow();
		assertEquals("github-token", record.key());
		assertEquals("ghp_****", record.maskedValue());
		assertFalse(record.maskedValue().contains("super-secret"));
		assertTrue(manager.contains("github-token"));
	}

	@Test
	void shouldReturnRawValueOnlyToSystemAndAuditAccess() {
		manager.put("token", "abc-12345");

		assertEquals("abc-12345", manager.get("token").orElseThrow());
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.SECRET_ACCESSED
				&& "token".equals(event.metadata().get("key"))));
	}

	@Test
	void shouldDenyAgentReadsAndAuditDenial() {
		manager.put("token", "abc-12345");

		assertTrue(manager.getForAgent("token", AgentType.CODEX).isEmpty());
		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.SECRET_DENIED
				&& "token".equals(event.metadata().get("key"))
				&& "CODEX".equals(event.metadata().get("agentType"))));
	}

	@Test
	void shouldRemoveSecrets() {
		manager.put("token", "abc-12345");

		manager.remove("token");

		assertFalse(manager.contains("token"));
		assertTrue(manager.get("token").isEmpty());
	}
}
