package com.aidevos.orchestrator.testagent;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds {@link TestReport} instances from a finished TestPlan. Passed/failed
 * counts are parsed from the captured test output (Maven Surefire or
 * Playwright/pytest style), with a status-based fallback. The report is also
 * persisted as report.json next to the test artifacts.
 */
@Component
public class TestReportGenerator {

	private static final Pattern SUREFIRE = Pattern.compile(
		"Tests run:\\s*(\\d+)\\s*,\\s*Failures:\\s*(\\d+)\\s*,\\s*Errors:\\s*(\\d+)");

	private static final Pattern PASSED_FAILED = Pattern.compile(
		"(\\d+)\\s+passed(?:,\\s*(\\d+)\\s+failed)?");

	private final ObjectMapper objectMapper;
	private final String artifactsDir;

	public TestReportGenerator(ObjectMapper objectMapper,
			@Value("${testagent.artifacts-dir:${user.dir}/test-artifacts}") String artifactsDir) {
		this.objectMapper = objectMapper;
		this.artifactsDir = artifactsDir;
	}

	public TestReport generate(TestPlan plan) {
		Counts counts = parseCounts(plan.getLogs());
		if (counts == null) {
			counts = new Counts(TestStatus.SUCCESS.equals(plan.getStatus()) ? 1 : 0,
				TestStatus.FAILED.equals(plan.getStatus()) ? 1 : 0);
		}
		String summary = plan.getResult() != null && !plan.getResult().isBlank()
			? plan.getResult()
			: (plan.getErrorMessage() != null && !plan.getErrorMessage().isBlank()
				? plan.getErrorMessage() : plan.getStatus().name());
		List<String> artifacts = new ArrayList<>();
		if (plan.getScreenshotPath() != null && !plan.getScreenshotPath().isBlank()) {
			artifacts.add("/api/tests/" + plan.getTestId() + "/screenshot");
		}
		artifacts.add("/api/tests/" + plan.getTestId() + "/report");
		return new TestReport(plan.getTestId(), summary, counts.passed(), counts.failed(),
			durationMs(plan), List.copyOf(artifacts));
	}

	/**
	 * Generates the report and persists it as report.json in the test artifact
	 * directory. The report is still returned when the file cannot be written.
	 */
	public TestReport generateAndStore(TestPlan plan) {
		TestReport report = generate(plan);
		writeJson(plan, report);
		return report;
	}

	private void writeJson(TestPlan plan, TestReport report) {
		try {
			File targetDir = new File(artifactsDir, plan.getTestId());
			if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
				return;
			}
			objectMapper.writerWithDefaultPrettyPrinter()
				.writeValue(new File(targetDir, "report.json"), report);
		}
		catch (RuntimeException ignored) {
			// The report is still returned over the API when the file cannot be written.
		}
	}

	private Counts parseCounts(String logs) {
		if (logs == null || logs.isBlank()) {
			return null;
		}
		Matcher surefire = SUREFIRE.matcher(logs);
		if (surefire.find()) {
			int run = Integer.parseInt(surefire.group(1));
			int failures = Integer.parseInt(surefire.group(2));
			int errors = Integer.parseInt(surefire.group(3));
			return new Counts(run - failures - errors, failures + errors);
		}
		Matcher passedFailed = PASSED_FAILED.matcher(logs);
		if (passedFailed.find()) {
			int passed = Integer.parseInt(passedFailed.group(1));
			int failed = passedFailed.group(2) == null ? 0 : Integer.parseInt(passedFailed.group(2));
			return new Counts(passed, failed);
		}
		return null;
	}

	private long durationMs(TestPlan plan) {
		if (plan.getStartedAt() == null || plan.getCompletedAt() == null) {
			return 0L;
		}
		return Math.max(0L, Duration.between(plan.getStartedAt(), plan.getCompletedAt()).toMillis());
	}

	private record Counts(int passed, int failed) {
	}
}
