package com.aidevos.orchestrator.validationplan;

import java.util.List;

import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VALIDATION-CENTER-V1-A 核心测试：多模式 Planning（不接执行链）。
 */
class ValidationPlanServiceTest {

	private static final class FakeAi implements AiValidationSelector {
		private final boolean available;
		private final AiPlan plan;
		private boolean invoked;

		private FakeAi(boolean available, AiPlan plan) {
			this.available = available;
			this.plan = plan;
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public AiPlan suggest(AiValidationInput input) {
			invoked = true;
			if (plan == null) {
				throw new RuntimeException("AI unavailable");
			}
			return plan;
		}
	}

	private ValidationPlanService service(FakeAi ai) {
		return new ValidationPlanService(new ChangeAnalyzer(), new LocalValidationSelector(),
			ai, new ValidationPlanComparator(), null);
	}

	private static ValidationCheck aiCheck(CheckType type) {
		return new ValidationCheck(type, "tool", "services/orchestrator",
			List.of("arg"), false, "ai suggested", CheckSource.AI, 300);
	}

	/** 1. Java test file change → LOCAL → targeted Maven test + workingDirectory 正确 + 0 AI */
	@Test
	void javaTestChangeLocalModeTargetsTestInModule() {
		FakeAi ai = new FakeAi(true, null);
		ValidationPlan plan = service(ai).generate("task-1", "change-1",
			List.of("services/orchestrator/src/test/java/com/aidevos/orchestrator/FooServiceTest.java"),
			ValidationMode.LOCAL, "TARGETED");

		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.MAVEN_TARGETED_TEST));
		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.BACKEND_COMPILE));
		ValidationCheck targeted = plan.checks().stream()
			.filter(c -> c.type() == CheckType.MAVEN_TARGETED_TEST).findFirst().orElseThrow();
		assertEquals("services/orchestrator", targeted.workingDirectory(),
			"workingDirectory 必须定位到模块根（含 pom.xml），不能是 repo root");
		assertFalse(ai.invoked, "LOCAL 模式 0 AI");
	}

	/** 2. Java production + 同名 Test → HIGH confidence → AUTO 不调 AI */
	@Test
	void autoHighConfidenceSkipsAi() {
		FakeAi ai = new FakeAi(true, null);
		ValidationPlan plan = service(ai).generate("task-2", "c2",
			List.of("services/orchestrator/src/main/java/com/aidevos/orchestrator/FooService.java"),
			ValidationMode.AUTO, null);

		assertEquals(ConfidenceLevel.HIGH, plan.confidence());
		assertFalse(ai.invoked, "HIGH confidence 的 AUTO 不得调用 AI");
	}

	/** 3. 复杂跨文件 change → MEDIUM/LOW → AUTO 调 AI → final = mandatory + local + AI */
	@Test
	void autoLowConfidenceInvokesAiAndUnions() {
		AiPlan aiPlan = new AiPlan(ConfidenceLevel.HIGH, List.of(
			aiCheck(CheckType.FRONTEND_TYPECHECK),
			aiCheck(CheckType.MAVEN_MODULE_TEST)));
		FakeAi ai = new FakeAi(true, aiPlan);
		ValidationPlan plan = service(ai).generate("task-3", "c3",
			List.of("services/orchestrator/src/main/java/com/aidevos/orchestrator/A.java",
				"services/orchestrator/src/main/java/com/aidevos/orchestrator/B.java",
				"services/orchestrator/src/main/java/com/aidevos/orchestrator/C.java",
				"services/orchestrator/src/main/java/com/aidevos/orchestrator/D.java"),
			ValidationMode.AUTO, null);

		assertTrue(ai.invoked, "LOW confidence 的 AUTO 必须调 AI");
		assertTrue(plan.checks().stream()
			.anyMatch(c -> c.source() == CheckSource.MANDATORY && c.type() == CheckType.BACKEND_COMPILE));
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.AI));
	}

	/** 4. VERIFY → Local 与 AI 都执行 → final 为安全并集 */
	@Test
	void verifyRunsBothAndUnions() {
		AiPlan aiPlan = new AiPlan(ConfidenceLevel.MEDIUM,
			List.of(aiCheck(CheckType.FRONTEND_TYPECHECK)));
		FakeAi ai = new FakeAi(true, aiPlan);
		ValidationPlan plan = service(ai).generate("task-4", "c4",
			List.of("services/orchestrator/src/test/java/com/x/FooTest.java"),
			ValidationMode.VERIFY, null);

		assertTrue(ai.invoked);
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.AI));
		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.MAVEN_TARGETED_TEST),
			"local targeted test 必须保留（并集）");
		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.BACKEND_COMPILE));
	}

	/** 5. AI 建议删除 mandatory check → mandatory 仍保留 */
	@Test
	void aiCannotRemoveMandatoryChecks() {
		AiPlan aiPlan = new AiPlan(ConfidenceLevel.HIGH,
			List.of(aiCheck(CheckType.FRONTEND_BUILD))); // AI 只给 additional，不含 mandatory
		FakeAi ai = new FakeAi(true, aiPlan);
		ValidationPlan plan = service(ai).generate("task-5", "c5",
			List.of("services/orchestrator/src/main/java/com/x/FooService.java"),
			ValidationMode.AI, null);

		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.MANDATORY
			&& c.type() == CheckType.BACKEND_COMPILE), "AI 不得删除 BACKEND_COMPILE mandatory");
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.MANDATORY
			&& c.type() == CheckType.GIT_DIFF_CHECK), "AI 不得删除 GIT_DIFF_CHECK mandatory");
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.AI));
	}

	/** 6. AI unavailable → fallback local broader/module validation → plan 仍成功 */
	@Test
	void aiUnavailableFallsBackToBroaderLocal() {
		FakeAi ai = new FakeAi(false, null); // unavailable
		ValidationPlan plan = service(ai).generate("task-6", "c6",
			List.of("services/orchestrator/src/main/java/com/x/A.java",
				"services/orchestrator/src/main/java/com/x/B.java",
				"services/orchestrator/src/main/java/com/x/C.java",
				"services/orchestrator/src/main/java/com/x/D.java"),
			ValidationMode.AUTO, null);

		assertNotNull(plan);
		assertEquals(ConfidenceLevel.LOW, plan.confidence());
		assertTrue(plan.fallbackUsed(), "AI 不可用必须标记 fallback");
		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.MAVEN_MODULE_TEST),
			"fallback 必须扩大为 module-level validation");
	}

	/** 7. docs-only → 只有 diff check → 不跑 Maven/frontend */
	@Test
	void docsOnlyHasOnlyDiffCheck() {
		FakeAi ai = new FakeAi(true, null);
		ValidationPlan plan = service(ai).generate("task-7", "c7",
			List.of("docs/architecture.md"), ValidationMode.AUTO, null);

		assertEquals(1, plan.checks().size());
		assertEquals(CheckType.GIT_DIFF_CHECK, plan.checks().get(0).type());
		assertEquals(CheckSource.MANDATORY, plan.checks().get(0).source());
	}

	/** 8. Local/AI 分歧过大 → disagreement → scope 扩大而不是缩小 */
	@Test
	void largeDisagreementEscalatesScope() {
		AiPlan aiPlan = new AiPlan(ConfidenceLevel.HIGH, List.of(
			aiCheck(CheckType.FRONTEND_TYPECHECK), aiCheck(CheckType.FRONTEND_BUILD),
			aiCheck(CheckType.FRONTEND_TARGETED_TEST), aiCheck(CheckType.MAVEN_MODULE_TEST),
			aiCheck(CheckType.BACKEND_COMPILE), aiCheck(CheckType.GIT_DIFF_CHECK),
			aiCheck(CheckType.MAVEN_TARGETED_TEST), aiCheck(CheckType.FRONTEND_TARGETED_TEST)));
		FakeAi ai = new FakeAi(true, aiPlan);
		ValidationPlan plan = service(ai).generate("task-8", "c8",
			List.of("services/orchestrator/src/test/java/com/x/FooTest.java"),
			ValidationMode.VERIFY, null);

		assertFalse(plan.disagreements().isEmpty(), "分歧过大必须记录 disagreement");
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.MERGED
			&& c.type() == CheckType.MAVEN_MODULE_TEST),
			"分歧过大必须扩大 scope（升级 module-level），绝不缩小");
	}
}
