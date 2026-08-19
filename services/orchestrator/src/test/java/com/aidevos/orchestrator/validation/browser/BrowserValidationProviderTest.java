package com.aidevos.orchestrator.validation.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.validation.InMemoryValidationArtifactRepository;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationEvidenceService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.validation.provider.ValidationCheckResult;
import com.aidevos.orchestrator.validation.provider.ValidationContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BrowserValidationProviderTest {
	@Test void executesStructuredActionAndAssertionAsSuccessfulEvidence() {
		FakeExecutor executor = new FakeExecutor(BrowserTestResult.success("navigated", null), BrowserTestResult.success("text matched", null));
		ValidationCheckResult result = provider(executor, "openclaw").execute(context(scenario(true)));
		assertEquals(ValidationStatus.SUCCESS, result.status()); assertEquals(2, executor.commands.size());
		assertTrue(executor.commands.get(0).contains("\"action\":\"navigate\""));
		assertTrue(executor.commands.get(1).contains("\"assertion\":\"text\""));
	}

	@Test void assertionFailureIsFailedAndCapturesScreenshotEvidence() {
		FakeExecutor executor = new FakeExecutor(BrowserTestResult.success("navigated", null),
			BrowserTestResult.failure(null, "Expected Dashboard but found Error", null),
			BrowserTestResult.success("captured", "/tmp/browser-failure.png"));
		ValidationCheckResult result = provider(executor, "openclaw").execute(context(scenario(true)));
		assertEquals(ValidationStatus.FAILED, result.status()); assertEquals("Expected Dashboard but found Error", result.errorMessage());
		assertFalse(((List<?>) result.metadata().get("artifactIds")).isEmpty());
	}

	@Test void unavailableRuntimeIsSkippedNotSuccessful() {
		ValidationCheckResult result = provider(new FakeExecutor(), "playwright").execute(context(scenario(true)));
		assertEquals(ValidationStatus.SKIPPED, result.status()); assertEquals("NOT_AVAILABLE", result.metadata().get("availability"));
	}

	private BrowserValidationProvider provider(BrowserTestExecutor executor, String configured) {
		return new BrowserValidationProvider(executor, new BrowserUrlPolicy(new BrowserScenarioProperties()),
			new ValidationEvidenceService(new InMemoryValidationArtifactRepository(), new ArtifactContentLimiter(100_000)),
			AuditService.noop(), new ObjectMapper(), configured);
	}
	private ValidationContext context(BrowserScenario scenario) { return new ValidationContext("run-1", "task-1", "project-1", "workspace-1", Path.of("."), ValidationCheckType.BROWSER, Map.of("browserScenario", scenario), false); }
	private BrowserScenario scenario(boolean required) { return new BrowserScenario("login", "Login", "http://127.0.0.1:4173/login", required,
		List.of(new BrowserStep("navigate", "Open login", BrowserAction.NAVIGATE, null, null, 5000L,
			List.of(new BrowserAssertion(BrowserAssertionType.TEXT, "body", "Dashboard", 5000L)), false, true))); }

	private static class FakeExecutor implements BrowserTestExecutor {
		private final Queue<BrowserTestResult> results = new ArrayDeque<>(); private final java.util.ArrayList<String> commands = new java.util.ArrayList<>();
		FakeExecutor(BrowserTestResult... results) { this.results.addAll(List.of(results)); }
		@Override public BrowserTestResult execute(String testId, String command) { commands.add(command); return results.isEmpty() ? BrowserTestResult.failure(null, "unexpected call", null) : results.remove(); }
	}
}
