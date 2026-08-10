package com.aidevos.orchestrator.security.secret;

import java.time.Instant;

/**
 * Stored secret metadata: the key, a masked preview and the creation time.
 * The raw value is never exposed outside the SecretManager.
 */
public record SecretRecord(String key, String maskedValue, Instant createdAt) {
}
