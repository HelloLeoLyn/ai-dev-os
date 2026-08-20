package com.aidevos.orchestrator.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central exception handling for the HTTP API. Replaces the per-controller
 * exception handlers and returns a unified {"code","message","timestamp"}
 * body while preserving the HTTP status semantics (400/404/500).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiError.of("NOT_FOUND", message(exception)));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(ApiError.of("ILLEGAL_ARGUMENT", message(exception)));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", "Malformed request body"));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiError.of("NOT_FOUND", "Resource not found"));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiError> handleIllegalState(IllegalStateException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiError.of("CONFLICT", message(exception)));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception) throws Exception {
		if (exception instanceof ErrorResponse) {
			throw exception;
		}
		log.error("Unhandled exception in API request", exception);
		return ResponseEntity.internalServerError()
			.body(ApiError.of("INTERNAL_ERROR", "Unexpected server error"));
	}

	private String message(Exception exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
