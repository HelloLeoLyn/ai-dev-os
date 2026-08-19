package com.aidevos.orchestrator.modelregistry;

/**
 * Fail-closed model resolution failure. The code is stable for execution
 * records and UI; the message carries the human-readable reason.
 */
public class ModelResolutionException extends RuntimeException {

	public enum Code {
		MODEL_NOT_FOUND,
		MODEL_DISABLED,
		PROVIDER_NOT_FOUND,
		PROVIDER_DISABLED,
		UNSUPPORTED_EXECUTOR,
		MODEL_EXECUTOR_MISMATCH,
		CREDENTIAL_MISSING
	}

	private final Code code;

	public ModelResolutionException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public Code code() {
		return code;
	}
}
