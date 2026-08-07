package com.aidevos.orchestrator.common.exception;

import java.time.Instant;

/**
 * Unified error response body: machine readable code, human readable message
 * and an ISO-8601 timestamp.
 */
public record ApiError(String code, String message, String timestamp) {

	public static ApiError of(String code, String message) {
		return new ApiError(code, message, Instant.now().toString());
	}
}
