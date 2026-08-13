package com.aidevos.orchestrator.validation.browser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationEvidenceService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.validation.provider.ValidationCheckResult;
import com.aidevos.orchestrator.validation.provider.ValidationContext;
import com.aidevos.orchestrator.validation.provider.ValidationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class BrowserValidationProvider implements ValidationProvider {
	private final BrowserTestExecutor executor;
	private final BrowserUrlPolicy urlPolicy;
	private final ValidationEvidenceService evidence;
	private final AuditService audit;
	private final ObjectMapper mapper;
	private final boolean openClawConfigured;

	public BrowserValidationProvider(BrowserTestExecutor executor, BrowserUrlPolicy urlPolicy,
			ValidationEvidenceService evidence, AuditService audit, ObjectMapper mapper,
			@Value("${testagent.browser.executor:playwright}") String executorName) {
		this.executor = executor; this.urlPolicy = urlPolicy; this.evidence = evidence;
		this.audit = audit; this.mapper = mapper;
		this.openClawConfigured = "openclaw".equalsIgnoreCase(executorName);
	}

	@Override public String name() { return "openclaw-browser"; }
	@Override public boolean supports(ValidationContext context) {
		return context.type() == ValidationCheckType.BROWSER
			&& context.capabilities().get("browserScenario") instanceof BrowserScenario;
	}

	@Override public ValidationCheckResult execute(ValidationContext context) {
		BrowserScenario scenario = (BrowserScenario) context.capabilities().get("browserScenario");
		if (!openClawConfigured) return unavailable(scenario);
		urlPolicy.requireAllowed(scenario.targetUrl());
		audit(context, EventType.BROWSER_VALIDATION_STARTED, "Browser validation started", scenario, null, Map.of());
		audit(context, EventType.BROWSER_SCENARIO_STARTED, "Browser scenario started", scenario, null, Map.of());
		List<BrowserStepResult> steps = new ArrayList<>(); List<String> artifacts = new ArrayList<>();
		String finalUrl = scenario.targetUrl(); ValidationStatus status = ValidationStatus.SUCCESS; String error = null;
		for (BrowserStep step : scenario.steps()) {
			Instant started = Instant.now();
			audit(context, EventType.BROWSER_STEP_STARTED, step.name() + " started", scenario, step, Map.of("action", step.action().name()));
			BrowserTestResult action = executor.execute(testId(context, scenario, step), command(scenario, step));
			if (!action.succeeded()) {
				status = ValidationStatus.ERROR; error = message(action);
				String screenshot = captureFailure(context, scenario, step, artifacts);
				steps.add(stepResult(step, status, started, action.output(), error, finalUrl, screenshot));
				break;
			}
			if (step.action() == BrowserAction.NAVIGATE) finalUrl = step.value() == null ? scenario.targetUrl() : step.value();
			String screenshot = saveScreenshot(context, scenario, step, action.screenshotPath(), artifacts);
			for (BrowserAssertion assertion : step.assertions()) {
				BrowserTestResult assertionResult = executor.execute(testId(context, scenario, step) + "-assert", assertionCommand(assertion));
				if (!assertionResult.succeeded()) {
					status = ValidationStatus.FAILED; error = message(assertionResult);
					audit(context, EventType.BROWSER_ASSERTION_FAILED, error, scenario, step, Map.of("assertion", assertion.type().name()));
					if (screenshot == null) screenshot = captureFailure(context, scenario, step, artifacts);
					break;
				}
			}
			if (status == ValidationStatus.SUCCESS && step.screenshotOnSuccess() && screenshot == null)
				screenshot = captureFailure(context, scenario, step, artifacts);
			steps.add(stepResult(step, status, started, action.output(), error, finalUrl, screenshot));
			audit(context, EventType.BROWSER_STEP_COMPLETED, step.name() + " " + status.name().toLowerCase(), scenario, step, Map.of("status", status.name()));
			if (status != ValidationStatus.SUCCESS) break;
		}
		BrowserValidationResult result = new BrowserValidationResult(scenario.scenarioId(), status, steps, finalUrl, error, artifacts);
		String logId = evidence.saveContent(context.validationRunId(), checkId(context, scenario), context.taskId(),
			scenario.scenarioId() + "-browser-result.json", "application/json", json(result), artifactMetadata(context, scenario, null, finalUrl));
		artifacts.add(logId);
		audit(context, EventType.BROWSER_SCENARIO_COMPLETED, "Browser scenario " + status.name().toLowerCase(), scenario, null, Map.of("status", status.name()));
		audit(context, EventType.BROWSER_VALIDATION_COMPLETED, "Browser validation completed", scenario, null, Map.of("status", status.name()));
		Map<String,Object> metadata = new LinkedHashMap<>(); metadata.put("scenarioId", scenario.scenarioId());
		metadata.put("steps", steps); metadata.put("stepCount", steps.size()); metadata.put("assertionCount", scenario.steps().stream().mapToInt(s -> s.assertions().size()).sum());
		metadata.put("finalUrl", finalUrl); metadata.put("availability", "AVAILABLE"); metadata.put("artifactIds", artifacts);
		return new ValidationCheckResult(status, steps.size() + " browser steps executed", error, null, null, List.of(), Map.copyOf(metadata));
	}

	private ValidationCheckResult unavailable(BrowserScenario scenario) {
		return new ValidationCheckResult(ValidationStatus.SKIPPED, "OpenClaw browser runtime is not available", null,
			null, null, List.of(), Map.of("availability", "NOT_AVAILABLE", "scenarioId", scenario.scenarioId()));
	}
	private String command(BrowserScenario scenario, BrowserStep step) {
		Map<String,Object> command = new LinkedHashMap<>(); command.put("action", step.action().name().toLowerCase());
		if (step.selector() != null) command.put("selector", step.selector());
		String value = step.action() == BrowserAction.NAVIGATE && step.value() == null ? scenario.targetUrl() : step.value();
		if (value != null) { if (step.action() == BrowserAction.NAVIGATE) urlPolicy.requireAllowed(value); command.put(step.action() == BrowserAction.NAVIGATE ? "url" : "value", value); }
		if (step.timeoutMs() != null) command.put("timeoutMs", step.timeoutMs()); return json(command);
	}
	private String assertionCommand(BrowserAssertion assertion) {
		Map<String,Object> command = new LinkedHashMap<>(); command.put("action", "assert"); command.put("assertion", assertion.type().name().toLowerCase());
		if (assertion.selector() != null) command.put("selector", assertion.selector()); if (assertion.expected() != null) command.put("expected", assertion.expected());
		if (assertion.timeoutMs() != null) command.put("timeoutMs", assertion.timeoutMs()); return json(command);
	}
	private String captureFailure(ValidationContext context, BrowserScenario scenario, BrowserStep step, List<String> artifacts) {
		BrowserTestResult capture = executor.execute(testId(context, scenario, step) + "-failure", "{\"action\":\"screenshot\"}");
		return saveScreenshot(context, scenario, step, capture.screenshotPath(), artifacts);
	}
	private String saveScreenshot(ValidationContext context, BrowserScenario scenario, BrowserStep step, String uri, List<String> artifacts) {
		if (uri == null || uri.isBlank()) return null;
		String id = evidence.saveReference(context.validationRunId(), checkId(context, scenario), context.taskId(), uri,
			"browser-screenshot.png", "image/png", artifactMetadata(context, scenario, step, scenario.targetUrl()));
		artifacts.add(id); audit(context, EventType.BROWSER_SCREENSHOT_CAPTURED, "Browser screenshot captured", scenario, step, Map.of("artifactId", id)); return id;
	}
	private BrowserStepResult stepResult(BrowserStep step, ValidationStatus status, Instant started, String summary, String error, String finalUrl, String screenshot) {
		return new BrowserStepResult(step.stepId(), step.name(), status, Duration.between(started, Instant.now()).toMillis(), summary, error, finalUrl, screenshot);
	}
	private Map<String,Object> artifactMetadata(ValidationContext context, BrowserScenario scenario, BrowserStep step, String url) {
		Map<String,Object> m = new LinkedHashMap<>(); m.put("taskId", context.taskId()); m.put("validationRunId", context.validationRunId());
		m.put("browserCheckId", checkId(context, scenario)); m.put("scenarioId", scenario.scenarioId()); if (step != null) m.put("stepId", step.stepId());
		m.put("url", url); m.put("viewport", "runtime-default"); m.put("timestamp", Instant.now().toString()); return Map.copyOf(m);
	}
	private void audit(ValidationContext context, EventType type, String summary, BrowserScenario scenario, BrowserStep step, Map<String,Object> extra) {
		Map<String,Object> m = new LinkedHashMap<>(artifactMetadata(context, scenario, step, scenario.targetUrl())); m.putAll(extra);
		audit.validationEvent(type, context.taskId(), context.validationRunId(), null, null, summary, Map.copyOf(m));
	}
	private String testId(ValidationContext c, BrowserScenario s, BrowserStep step) { return c.validationRunId() + "-" + s.scenarioId() + "-" + step.stepId(); }
	private String checkId(ValidationContext context, BrowserScenario scenario) { Object value = context.capabilities().get("validationCheckId"); return value == null ? "browser-" + scenario.scenarioId() : value.toString(); }
	private String message(BrowserTestResult result) { return result.errorMessage() == null || result.errorMessage().isBlank() ? "Browser operation failed" : result.errorMessage(); }
	private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Browser operation cannot be serialized", e); } }
}
