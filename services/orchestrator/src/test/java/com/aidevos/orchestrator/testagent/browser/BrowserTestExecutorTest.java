package com.aidevos.orchestrator.testagent.browser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.aidevos.orchestrator.browser.BrowserResultMapper;
import com.aidevos.orchestrator.browser.BrowserTaskPromptBuilder;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrowserTestExecutorTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldRunPlaywrightCommandAndCaptureScreenshot() throws Exception {
		Path workdir = Files.createDirectories(tempDir.resolve("project"));
		Path artifacts = Files.createDirectories(tempDir.resolve("artifacts"));
		PlaywrightBrowserTestExecutor executor = new PlaywrightBrowserTestExecutor(
			workdir.toString(), "", artifacts.toString(), Duration.ofSeconds(30),
			"npx playwright test");

		BrowserTestResult result = executor.execute("test-1",
			"mkdir -p test-results && printf 'PNG' > test-results/shot-1.png");

		assertTrue(result.succeeded());
		assertNotNull(result.screenshotPath());
		assertTrue(Files.exists(Path.of(result.screenshotPath())));
		assertTrue(Files.isRegularFile(artifacts.resolve("test-1/screenshot.png")));
	}

	@Test
	void shouldReportFailureWithExitCode() throws Exception {
		Path workdir = Files.createDirectories(tempDir.resolve("project"));
		Path artifacts = Files.createDirectories(tempDir.resolve("artifacts"));
		PlaywrightBrowserTestExecutor executor = new PlaywrightBrowserTestExecutor(
			workdir.toString(), "", artifacts.toString(), Duration.ofSeconds(30),
			"npx playwright test");

		BrowserTestResult result = executor.execute("test-1", "exit 3");

		assertTrue(!result.succeeded());
		assertEquals("exit code 3", result.errorMessage());
	}

	@Test
	void shouldReturnNullScreenshotWhenNoneCaptured() throws Exception {
		Path workdir = Files.createDirectories(tempDir.resolve("project"));
		Path artifacts = Files.createDirectories(tempDir.resolve("artifacts"));
		PlaywrightBrowserTestExecutor executor = new PlaywrightBrowserTestExecutor(
			workdir.toString(), "", artifacts.toString(), Duration.ofSeconds(30),
			"npx playwright test");

		BrowserTestResult result = executor.execute("test-1", "true");

		assertTrue(result.succeeded());
		assertNull(result.screenshotPath());
	}

	@Test
	void shouldFallBackToDefaultCommandWhenCommandBlank() throws Exception {
		Path workdir = Files.createDirectories(tempDir.resolve("project"));
		Path artifacts = Files.createDirectories(tempDir.resolve("artifacts"));
		PlaywrightBrowserTestExecutor executor = new PlaywrightBrowserTestExecutor(
			workdir.toString(), "", artifacts.toString(), Duration.ofSeconds(30),
			"echo default-command-ran");

		BrowserTestResult result = executor.execute("test-1", null);

		assertTrue(result.succeeded());
		assertTrue(result.output().contains("default-command-ran"));
	}

	@Test
	void openClawExecutorRoutesBrowserOperationThroughGateway() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		ObjectMapper mapper = new ObjectMapper();
		OpenClawTaskResult taskResult = new OpenClawTaskResult("run-1", "session-1", "ok",
			"{\"output\":\"navigated\",\"artifacts\":["
				+ "{\"type\":\"screenshot\",\"name\":\"shot.png\",\"mediaType\":\"image/png\","
				+ "\"uri\":\"/tmp/shot.png\"}]}");
		when(taskService.execute(any(OpenClawTaskRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(taskResult));
		OpenClawBrowserTestExecutor executor = new OpenClawBrowserTestExecutor(taskService,
			new BrowserTaskPromptBuilder(mapper), new BrowserResultMapper(mapper), mapper,
			"browser-agent");

		BrowserTestResult result = executor.execute("test-1", "https://example.com");

		assertTrue(result.succeeded());
		assertEquals("navigated", result.output());
		assertEquals("/tmp/shot.png", result.screenshotPath());
	}

	@Test
	void openClawExecutorReportsGatewayFailure() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		ObjectMapper mapper = new ObjectMapper();
		OpenClawTaskResult taskResult = new OpenClawTaskResult("run-1", "session-1", "error", null);
		when(taskService.execute(any(OpenClawTaskRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(taskResult));
		OpenClawBrowserTestExecutor executor = new OpenClawBrowserTestExecutor(taskService,
			new BrowserTaskPromptBuilder(mapper), new BrowserResultMapper(mapper), mapper,
			"browser-agent");

		BrowserTestResult result = executor.execute("test-1", "https://example.com");

		assertTrue(!result.succeeded());
		assertTrue(result.errorMessage().contains("status error"));
	}

	@Test
	void openClawExecutorReportsStructuredAssertionFailure() {
		OpenClawTaskService taskService = mock(OpenClawTaskService.class);
		ObjectMapper mapper = new ObjectMapper();
		OpenClawTaskResult taskResult = new OpenClawTaskResult("run-1", "session-1", "ok",
			"{\"succeeded\":false,\"output\":\"assertion failed\","
				+ "\"errorMessage\":\"Expected Saved but was Error\",\"artifacts\":["
				+ "{\"type\":\"screenshot\",\"uri\":\"/tmp/failure.png\"}]}");
		when(taskService.execute(any(OpenClawTaskRequest.class)))
			.thenReturn(CompletableFuture.completedFuture(taskResult));
		OpenClawBrowserTestExecutor executor = new OpenClawBrowserTestExecutor(taskService,
			new BrowserTaskPromptBuilder(mapper), new BrowserResultMapper(mapper), mapper,
			"browser-agent");

		BrowserTestResult result = executor.execute("test-1", "https://example.com");

		assertFalse(result.succeeded());
		assertEquals("Expected Saved but was Error", result.errorMessage());
		assertEquals("/tmp/failure.png", result.screenshotPath());
	}
}
