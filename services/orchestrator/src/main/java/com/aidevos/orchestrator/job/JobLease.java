package com.aidevos.orchestrator.job;

import java.time.Instant;

/**
 * Lease granting a single worker exclusive ownership of a job until it expires.
 * The token is a monotonic fencing value: any write must carry the latest token,
 * otherwise the lease has been superseded and the write must be rejected.
 */
public record JobLease(String owner, long token, Instant expiresAt) {

	public JobLease {
		if (owner == null || owner.isBlank()) {
			throw new IllegalArgumentException("Lease owner is required");
		}
		if (expiresAt == null) {
			throw new IllegalArgumentException("Lease expiry is required");
		}
	}
}
