package com.aidevos.orchestrator.testagent;

import java.util.List;

/**
 * JSON test report for a TestPlan: a summary of the run, passed/failed counts,
 * duration in milliseconds and the artifact URLs that belong to the test.
 */
public record TestReport(
		String testId,
		String summary,
		int passed,
		int failed,
		long duration,
		List<String> artifacts) {
}
