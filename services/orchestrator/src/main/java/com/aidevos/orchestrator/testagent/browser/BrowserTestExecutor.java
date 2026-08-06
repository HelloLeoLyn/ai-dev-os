package com.aidevos.orchestrator.testagent.browser;

/**
 * Abstraction for executing browser tests (UI_TEST). Implementations run the
 * Playwright test command locally or route the browser operation through the
 * existing OpenClaw gateway, and capture a screenshot when one is produced.
 */
public interface BrowserTestExecutor {

	/**
	 * Executes a browser test and captures a screenshot artifact.
	 *
	 * @param testId the test plan id (used to namespace artifacts)
	 * @param command the Playwright test command or browser operation
	 * @return the outcome including success flag, output, error and screenshot path
	 */
	BrowserTestResult execute(String testId, String command);
}
