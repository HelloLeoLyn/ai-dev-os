package com.aidevos.orchestrator.validation.provider;

public interface ValidationProvider {
	boolean supports(ValidationContext context);
	ValidationCheckResult execute(ValidationContext context);
	String name();
}
