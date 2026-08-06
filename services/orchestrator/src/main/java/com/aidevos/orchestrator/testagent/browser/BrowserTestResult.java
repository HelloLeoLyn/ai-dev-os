package com.aidevos.orchestrator.testagent.browser;

/**
 * Outcome of a browser test: whether it succeeded, the captured output, an
 * error description and the path of the captured screenshot (may be null).
 */
public record BrowserTestResult(
		boolean succeeded,
		String output,
		String errorMessage,
		String screenshotPath) {

	public static BrowserTestResult success(String output, String screenshotPath) {
		return new BrowserTestResult(true, output, null, screenshotPath);
	}

	public static BrowserTestResult failure(String output, String errorMessage, String screenshotPath) {
		return new BrowserTestResult(false, output, errorMessage, screenshotPath);
	}
}
