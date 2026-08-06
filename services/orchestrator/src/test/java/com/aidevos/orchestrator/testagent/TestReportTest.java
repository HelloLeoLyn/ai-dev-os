package com.aidevos.orchestrator.testagent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReportTest {

	@TempDir
	Path tempDir;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldParseSurefireCounts() {
		TestPlan plan = plan("test-1");
		plan.markRunning();
		plan.markSuccess("exit code 0",
			"Tests run: 10, Failures: 2, Errors: 1\nBUILD FAILURE");

		TestReport report = generator().generate(plan);

		assertEquals(7, report.passed());
		assertEquals(3, report.failed());
		assertEquals("exit code 0", report.summary());
	}

	@Test
	void shouldParsePlaywrightCounts() {
		TestPlan plan = plan("test-1");
		plan.markRunning();
		plan.markSuccess("browser test succeeded", "2 passed, 1 failed (12.3s)");

		TestReport report = generator().generate(plan);

		assertEquals(2, report.passed());
		assertEquals(1, report.failed());
	}

	@Test
	void shouldFallBackToStatusBasedCounts() {
		TestPlan plan = plan("test-1");
		plan.markRunning();
		plan.markSuccess("exit code 0", "BUILD SUCCESS");

		TestReport report = generator().generate(plan);

		assertEquals(1, report.passed());
		assertEquals(0, report.failed());
	}

	@Test
	void shouldComputeDurationAndIncludeArtifacts() throws Exception {
		TestPlan plan = plan("test-1");
		plan.markRunning();
		Thread.sleep(5);
		plan.markSuccess("exit code 0", "BUILD SUCCESS");
		plan.setScreenshotPath("/tmp/screenshot.png");

		TestReport report = generator().generate(plan);

		assertTrue(report.duration() >= 5);
		assertEquals(2, report.artifacts().size());
		assertEquals("/api/tests/test-1/screenshot", report.artifacts().getFirst());
		assertEquals("/api/tests/test-1/report", report.artifacts().get(1));
	}

	@Test
	void shouldWriteJsonReportFile() throws Exception {
		TestPlan plan = plan("test-1");
		plan.markRunning();
		plan.markSuccess("exit code 0", "1 passed");
		TestReportGenerator generator = new TestReportGenerator(objectMapper,
			tempDir.toString());

		TestReport report = generator.generateAndStore(plan);

		File reportFile = tempDir.resolve("test-1/report.json").toFile();
		assertTrue(reportFile.isFile());
		JsonNode node = objectMapper.readTree(Files.readString(reportFile.toPath()));
		assertEquals("test-1", node.get("testId").asText());
		assertEquals(report.passed(), node.get("passed").asInt());
	}

	private TestReportGenerator generator() {
		return new TestReportGenerator(objectMapper, tempDir.toString());
	}

	private TestPlan plan(String testId) {
		return new TestPlan(testId, null, TestType.UNIT_TEST, "mvn test", "default", null);
	}
}
