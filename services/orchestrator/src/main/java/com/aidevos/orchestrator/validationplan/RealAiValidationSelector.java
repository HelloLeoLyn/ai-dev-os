package com.aidevos.orchestrator.validationplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.modelregistry.ModelResolver;
import com.aidevos.orchestrator.modelregistry.ResolvedModel;
import com.aidevos.orchestrator.validationplan.TestCatalogService.CatalogTest;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 真实 AI Validation Selector。
 *
 * 复用 ModelResolver（provider/baseUrl/credentialRef/model）+ AiProviderClient（HTTP）。
 * 输入严格压缩（goal/files/diff summary/module/candidates/catalog/risk/confidence），
 * 禁止发送整个 repository。
 *
 * 输出必须是结构化 JSON：{"suggestedChecks":[{"type","testId","reason"}],"confidence"}
 * 校验规则（fail safe）：
 * - type 必须 ∈ 允许的 CheckType
 * - testId 必须 ∈ testCatalog（AI 不能发明测试）
 * - 禁止生成 shell/任意路径/Maven 参数（check 只由 type+testId 派生）
 * 输出非法 / provider 失败 → 抛 AiUnavailableException → 调用方 broader local fallback。
 */
@Component
@ConditionalOnProperty(name = "aidevos.validation.ai-selector", havingValue = "enabled")
public class RealAiValidationSelector implements AiValidationSelector {

	private static final String DEFAULT_MODEL = "deepseek-v4-flash";
	private static final Set<String> ALLOWED_TYPES = Set.of(
		"GIT_DIFF_CHECK", "BACKEND_COMPILE", "MAVEN_TARGETED_TEST", "MAVEN_MODULE_TEST",
		"FRONTEND_TYPECHECK", "FRONTEND_TARGETED_TEST", "FRONTEND_BUILD");

	private final ModelResolver modelResolver;
	private final AiProviderClient providerClient;
	private final ObjectMapper mapper;
	private final String modelOverride;

	public RealAiValidationSelector(ModelResolver modelResolver, AiProviderClient providerClient,
			ObjectMapper mapper,
			@Value("${aidevos.validation.ai-model:}") String modelOverride) {
		this.modelResolver = modelResolver;
		this.providerClient = providerClient;
		this.mapper = mapper;
		this.modelOverride = modelOverride;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public String providerId() {
		try {
			return resolveModel().providerId();
		}
		catch (RuntimeException exception) {
			return "";
		}
	}

	@Override
	public String modelId() {
		try {
			return resolveModel().resolvedModelId();
		}
		catch (RuntimeException exception) {
			return "";
		}
	}

	@Override
	public AiPlan suggest(AiValidationInput input) {
		ResolvedModel resolved = resolveModel();
		String systemPrompt = "You are a validation planner. Respond ONLY with JSON in this "
			+ "exact shape: {\"suggestedChecks\":[{\"type\":\"<CHECK_TYPE>\","
			+ "\"testId\":\"<catalog testId or empty>\",\"reason\":\"<short reason>\"}],"
			+ "\"confidence\":\"HIGH|MEDIUM|LOW\"}. CHECK_TYPE must be one of "
			+ ALLOWED_TYPES + ". testId must be one of the provided catalog test ids "
			+ "or empty. Never invent tests, paths, shell commands or Maven arguments.";
		String userPrompt = buildUserPrompt(input);
		String content = providerClient.chatCompletion(resolved.baseUrl(),
			credentialValue(resolved.credentialRef()), resolved.resolvedModelId(),
			systemPrompt, userPrompt);
		return parse(content, input.testCatalog(), input.module());
	}

	private ResolvedModel resolveModel() {
		String model = modelOverride == null || modelOverride.isBlank()
			? DEFAULT_MODEL : modelOverride.trim();
		return modelResolver.resolve(null, model);
	}

	private String credentialValue(String credentialRef) {
		if (credentialRef == null || credentialRef.isBlank()) {
			throw new IllegalStateException("AI provider credential reference is missing");
		}
		String value = lookupEnv(credentialRef);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("AI provider credential not set: " + credentialRef);
		}
		return value;
	}

	protected String lookupEnv(String name) {
		return System.getenv(name);
	}

	private String buildUserPrompt(AiValidationInput input) {
		Map<String, Object> payload = Map.of(
			"taskGoal", input.taskGoal() == null ? "" : input.taskGoal(),
			"changedFiles", input.changedFiles() == null ? List.of() : input.changedFiles(),
			"diffSummary", input.diffSummary() == null ? "" : input.diffSummary(),
			"module", input.module() == null ? "" : input.module(),
			"localCandidateTests", input.localCandidateTests() == null
				? List.of() : input.localCandidateTests(),
			"testCatalog", input.testCatalog() == null ? List.of() : input.testCatalog()
				.stream().limit(100).toList(),
			"localRisk", String.valueOf(input.localRisk()),
			"localConfidence", String.valueOf(input.localConfidence()));
		try {
			return mapper.writeValueAsString(payload);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to serialize AI input", exception);
		}
	}

	/** 解析 + 严格校验；任何非法输出 → AiUnavailableException（fail safe）。 */
	private AiPlan parse(String content, List<String> testCatalog, String module) {
		try {
			Map<?, ?> json = mapper.readValue(content, Map.class);
			java.util.List<?> rawChecks = (java.util.List<?>) json.get("suggestedChecks");
			if (rawChecks == null) {
				throw new IllegalStateException("AI output missing suggestedChecks");
			}
			List<ValidationCheck> checks = new ArrayList<>();
			Set<String> catalog = new java.util.HashSet<>(
				testCatalog == null ? List.of() : testCatalog);
			for (Object raw : rawChecks) {
				Map<?, ?> item = (Map<?, ?>) raw;
				String type = String.valueOf(item.get("type"));
				String testId = item.get("testId") == null ? "" : String.valueOf(item.get("testId"));
				String reason = item.get("reason") == null ? "AI suggested"
					: String.valueOf(item.get("reason"));
				if (!ALLOWED_TYPES.contains(type)) {
					throw new IllegalStateException("AI suggested disallowed check type: " + type);
				}
				if (!testId.isBlank() && !catalog.contains(testId)) {
					throw new IllegalStateException("AI suggested unknown test: " + testId);
				}
				checks.add(toCheck(type, testId, reason, module));
			}
			String confidence = json.get("confidence") == null ? "MEDIUM"
				: String.valueOf(json.get("confidence")).toUpperCase();
			ConfidenceLevel level = switch (confidence) {
				case "HIGH" -> ConfidenceLevel.HIGH;
				case "LOW" -> ConfidenceLevel.LOW;
				default -> ConfidenceLevel.MEDIUM;
			};
			return new AiPlan(level, List.copyOf(checks));
		}
		catch (DisabledAiValidationSelector.AiUnavailableException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new DisabledAiValidationSelector.AiUnavailableException(
				"AI output invalid: " + exception.getMessage());
		}
	}

	/** check 只由 type + testId 派生（无 shell / 无任意路径 / 无任意 Maven 参数）。 */
	private ValidationCheck toCheck(String type, String testId, String reason, String module) {
		CheckType checkType = CheckType.valueOf(type);
		String tool = switch (checkType) {
			case GIT_DIFF_CHECK -> "git";
			case BACKEND_COMPILE, MAVEN_TARGETED_TEST, MAVEN_MODULE_TEST -> "maven";
			default -> "npm";
		};
		List<String> arguments = switch (checkType) {
			case GIT_DIFF_CHECK -> List.of("diff", "HEAD");
			case BACKEND_COMPILE -> List.of("compile");
			case MAVEN_TARGETED_TEST -> testId.isBlank() ? List.of("test")
				: List.of("test", "-Dtest=" + testId.substring(testId.lastIndexOf('/') + 1)
					.replace("Test.java", "Test").replace("Tests.java", "Tests"));
			case MAVEN_MODULE_TEST -> List.of("test");
			case FRONTEND_TYPECHECK -> List.of("run", "type-check", "--", "vue-tsc", "--noEmit");
			case FRONTEND_TARGETED_TEST -> testId.isBlank() ? List.of("test")
				: List.of("test", "--", testId);
			case FRONTEND_BUILD -> List.of("run", "build");
		};
		return new ValidationCheck(checkType, tool, module == null ? "." : module,
			arguments, false, reason, CheckSource.AI, 300);
	}
}
