package com.aidevos.orchestrator.validation.browser;

public record BrowserAssertion(BrowserAssertionType type, String selector, String expected,
		Long timeoutMs) { }
