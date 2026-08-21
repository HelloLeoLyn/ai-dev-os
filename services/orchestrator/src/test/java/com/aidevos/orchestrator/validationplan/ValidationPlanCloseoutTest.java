package com.aidevos.orchestrator.validationplan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aidevos.orchestrator.modelregistry.ModelResolver;
import com.aidevos.orchestrator.modelregistry.ResolvedModel;
import com.aidevos.orchestrator.validationplan.TestCatalogService.CatalogTest;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * VALIDATION-CENTER-V1-A-CLOSEOUT：真实 AI selector + test catalog。
 */
class ValidationPlanCloseoutTest {

	@TempDir
	Path tempRoot;

	private TestCatalogService catalogService;
	private AiProviderClient providerClient;
	private ModelResolver modelResolver;

	@BeforeEach
	void setUp() throws IOException {
		createModuleTree();
		catalogService = new TestCatalogService(tempRoot.toString());
		providerClient = mock(AiProviderClient.class);
		modelResolver = mock(ModelResolver.class);
		when(modelResolver.resolve(null, "deepseek-v4-flash")).thenReturn(
			new ResolvedModel("AUTO", "deepseek-v4-flash", "deepseek", "codex",
				"https://api.deepseek.com", "DEEPSEEK_API_KEY"));
	}

	private void createModuleTree() throws IOException {
		write("services/orchestrator/src/test/java/com/aidevos/FooServiceTest.java",
			"class FooServiceTest {}");
		write("services/orchestrator/src/test/java/com/aidevos/BarTests.java",
			"class BarTests {}");
		write("services/orchestrator/frontend/src/components/Button.spec.ts",
			"import {} from 'vue'");
		write("services/orchestrator/frontend/src/components/Input.test.js", "");
		// 干扰：不得扫描
		write("services/orchestrator/target/classes/CompiledTest.java", "class CompiledTest {}");
		write("services/orchestrator/frontend/node_modules/pkg/Fake.test.ts", "");
		write("services/orchestrator/frontend/dist/Bundle.test.ts", "");
	}

	private void write(String relative, String content) throws IOException {
		Path path = tempRoot.resolve(relative);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}

	private RealAiValidationSelector realSelector(String json) {
		when(providerClient.chatCompletion(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(json);
		RealAiValidationSelector selector = new RealAiValidationSelector(modelResolver,
			providerClient, new ObjectMapper(), "") {
			@Override
			protected String lookupEnv(String name) {
				return "test-key";
			}
		};
		return selector;
	}

	private ValidationPlanService service(AiValidationSelector ai) {
		return new ValidationPlanService(new ChangeAnalyzer(), new LocalValidationSelector(),
			ai, new ValidationPlanComparator(), catalogService);
	}

	private static List<String> fourFiles() {
		return List.of("services/orchestrator/src/main/java/com/aidevos/A.java",
			"services/orchestrator/src/main/java/com/aidevos/B.java",
			"services/orchestrator/src/main/java/com/aidevos/C.java",
			"services/orchestrator/src/main/java/com/aidevos/D.java");
	}

	/** 1. test catalog 扫描 module → 找到 Java/Frontend tests，不扫 target/node_modules/dist */
	@Test
	void catalogScansModuleAndExcludesBuildDirs() {
		List<CatalogTest> tests = catalogService.scan("services/orchestrator");

		assertTrue(tests.stream().anyMatch(t -> t.testId().endsWith("FooServiceTest")),
			"必须找到 FooServiceTest");
		assertTrue(tests.stream().anyMatch(t -> t.testId().endsWith("BarTests")),
			"必须找到 BarTests");
		assertTrue(tests.stream().anyMatch(t -> t.testId().endsWith("Button.spec")),
			"必须找到 frontend spec");
		assertTrue(tests.stream().noneMatch(t -> t.testId().contains("target")),
			"不得扫描 target");
		assertTrue(tests.stream().noneMatch(t -> t.testId().contains("node_modules")),
			"不得扫描 node_modules");
		assertTrue(tests.stream().noneMatch(t -> t.testId().contains("dist")),
			"不得扫描 dist");
	}

	/** 2. AUTO LOW confidence → 调真实 selector adapter → AI 只能选择 catalog 中测试 */
	@Test
	void autoLowConfidenceUsesRealSelectorWithCatalogOnly() {
		RealAiValidationSelector ai = realSelector("{\"suggestedChecks\":["
			+ "{\"type\":\"MAVEN_TARGETED_TEST\","
			+ "\"testId\":\"src/test/java/com/aidevos/FooServiceTest\","
			+ "\"reason\":\"cover change\"}],\"confidence\":\"MEDIUM\"}");
		ValidationPlan plan = service(ai).generate("task-2", "c2", fourFiles(),
			ValidationMode.AUTO, null);

		assertEquals(ConfidenceLevel.LOW, plan.confidence());
		assertNotNull(plan.aiPlan(), "AUTO LOW 必须调用真实 AI selector");
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.AI
			&& c.type() == CheckType.MAVEN_TARGETED_TEST),
			"final plan 必须包含 AI 从 catalog 选择的测试");
	}

	/** 3. AI 返回不存在测试 → 拒绝 → broader local fallback */
	@Test
	void aiSuggestingUnknownTestFallsBackToBroaderLocal() {
		RealAiValidationSelector ai = realSelector("{\"suggestedChecks\":["
			+ "{\"type\":\"MAVEN_TARGETED_TEST\",\"testId\":\"com/ghost/GhostTest\","
			+ "\"reason\":\"invented\"}],\"confidence\":\"HIGH\"}");
		ValidationPlan plan = service(ai).generate("task-3", "c3", fourFiles(),
			ValidationMode.AUTO, null);

		assertTrue(plan.fallbackUsed(), "AI 输出非法（不存在测试）必须 fallback");
		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.MAVEN_MODULE_TEST),
			"fallback 必须扩大为 broader module validation");
	}

	/** 4. VERIFY → Local + AI → UNION → mandatory 保留 */
	@Test
	void verifyUnionsLocalAndAiKeepingMandatory() {
		RealAiValidationSelector ai = realSelector("{\"suggestedChecks\":["
			+ "{\"type\":\"FRONTEND_TYPECHECK\",\"testId\":\"\",\"reason\":\"extra safety\"}],"
			+ "\"confidence\":\"MEDIUM\"}");
		ValidationPlan plan = service(ai).generate("task-4", "c4",
			List.of("services/orchestrator/src/main/java/com/aidevos/FooService.java"),
			ValidationMode.VERIFY, null);

		assertNotNull(plan.aiPlan());
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.MANDATORY
			&& c.type() == CheckType.BACKEND_COMPILE), "mandatory 必须保留");
		assertTrue(plan.checks().stream().anyMatch(c -> c.source() == CheckSource.AI
			&& c.type() == CheckType.FRONTEND_TYPECHECK), "AI 补充必须加入（UNION）");
	}

	/** 5. AI provider failure → fallbackUsed=true → plan 成功 */
	@Test
	void aiProviderFailureFallsBackAndPlanStillSucceeds() {
		when(providerClient.chatCompletion(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
			.thenThrow(new IllegalStateException("HTTP 401 Unauthorized"));
		RealAiValidationSelector ai = new RealAiValidationSelector(modelResolver,
			providerClient, new ObjectMapper(), "") {
			@Override
			protected String lookupEnv(String name) {
				return "test-key";
			}
		};
		ValidationPlan plan = service(ai).generate("task-5", "c5", fourFiles(),
			ValidationMode.AUTO, null);

		assertNotNull(plan);
		assertTrue(plan.fallbackUsed(), "AI provider 失败必须标记 fallback");
		assertFalse(plan.aiPlan() != null, "AI 失败后不得产生 aiPlan");
		assertTrue(plan.checks().stream().anyMatch(c -> c.type() == CheckType.MAVEN_MODULE_TEST),
			"fallback 计划必须可执行");
	}
}
