package com.aidevos.orchestrator.execution;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionBudgetTest {

	@Test
	void resolveWithNoMetadataUsesFastDefaults() {
		ExecutionBudget budget = ExecutionBudget.resolve(Map.of());

		assertEquals(ValidationProfile.FAST, budget.validationProfile());
		assertEquals(20, budget.maxAiCalls());
		assertEquals(1, budget.maxToolRetries());
		assertEquals(List.of(), budget.stopConditions());
	}

	@Test
	void resolveWithMetadataOverridesProfileRetriesAndConditions() {
		ExecutionBudget budget = ExecutionBudget.resolve(Map.of(
			"validationProfile", "TARGETED",
			"maxAiCalls", 5,
			"maxToolRetries", 3,
			"stopConditions", List.of("tests-passed", "diff-clean")));

		assertEquals(ValidationProfile.TARGETED, budget.validationProfile());
		assertEquals(5, budget.maxAiCalls());
		assertEquals(3, budget.maxToolRetries());
		assertEquals(List.of("tests-passed", "diff-clean"), budget.stopConditions());
	}

	@Test
	void resolveFallsBackOnGarbageValues() {
		ExecutionBudget budget = ExecutionBudget.resolve(Map.of(
			"validationProfile", "ULTRA",
			"maxToolRetries", -5,
			"maxAiCalls", 0,
			"stopConditions", "diff-clean, health-ok"));

		assertEquals(ValidationProfile.FAST, budget.validationProfile());
		assertEquals(20, budget.maxAiCalls());
		assertEquals(1, budget.maxToolRetries());
		assertEquals(List.of("diff-clean", "health-ok"), budget.stopConditions());
	}
}
