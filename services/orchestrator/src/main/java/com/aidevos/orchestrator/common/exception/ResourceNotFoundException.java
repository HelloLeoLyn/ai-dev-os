package com.aidevos.orchestrator.common.exception;

/**
 * Thrown when a requested resource does not exist. Mapped by
 * GlobalExceptionHandler to HTTP 404 with a unified error body.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public ResourceNotFoundException(String resource, String id) {
		super(resource + " not found: " + id);
	}
}
