package com.aidevos.orchestrator.security.secret;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory secret store. Raw values never leave this class; every access is
 * audited and agent reads are denied by default (SECRET_DENIED).
 */
@Component
public class SecretManager {

	private final Map<String, String> secrets = new ConcurrentHashMap<>();
	private final Map<String, SecretRecord> records = new ConcurrentHashMap<>();
	private final AuditService auditService;

	public SecretManager() {
		this(AuditService.noop());
	}

	@Autowired
	public SecretManager(AuditService auditService) {
		this.auditService = auditService;
	}

	public void put(String key, String value) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Secret key is required");
		}
		if (value == null) {
			remove(key);
			return;
		}
		secrets.put(key, value);
		records.put(key, new SecretRecord(key, mask(value), Instant.now()));
	}

	public Optional<String> get(String key) {
		String value = secrets.get(key);
		if (value == null) {
			return Optional.empty();
		}
		auditService.secretEvent(EventType.SECRET_ACCESSED, key, null, null,
			"Secret accessed by system", Map.of("key", key));
		return Optional.of(value);
	}

	/**
	 * Agent reads are denied by default: the secret is never returned and a
	 * SECRET_DENIED audit event is recorded.
	 */
	public Optional<String> getForAgent(String key, AgentType agentType) {
		auditService.secretEvent(EventType.SECRET_DENIED, key,
			agentType == null ? null : agentType.name(), null,
			"Agent secret access denied", Map.of("key", key,
				"agentType", agentType == null ? "" : agentType.name()));
		return Optional.empty();
	}

	public Optional<SecretRecord> remove(String key) {
		String value = secrets.remove(key);
		SecretRecord record = records.remove(key);
		return Optional.ofNullable(record != null ? record
			: (value == null ? null
				: new SecretRecord(key, mask(value), Instant.now())));
	}

	public Optional<SecretRecord> getRecord(String key) {
		return Optional.ofNullable(records.get(key));
	}

	public boolean contains(String key) {
		return secrets.containsKey(key);
	}

	private String mask(String value) {
		if (value.length() <= 4) {
			return "****";
		}
		return value.substring(0, 4) + "****";
	}
}
