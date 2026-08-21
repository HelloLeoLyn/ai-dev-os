package com.aidevos.orchestrator.validationplan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.LocalPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationMode;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ValidationPlanService：多模式 Validation Planning 编排。
 *
 * LOCAL → 纯确定性，0 LLM。
 * AI    → mandatory 保留，AI 只补充。
 * AUTO  → HIGH confidence 直接 LOCAL；MEDIUM/LOW 调 AI；AI 不可用 → Local broader fallback。
 * VERIFY→ Local 与 AI 都执行，安全并集；分歧过大 → scope 扩大。
 *
 * AI 永不因不可用导致 Planning 失败。
 */
@Service
public class ValidationPlanService {

	private final ChangeAnalyzer analyzer;
	private final LocalValidationSelector localSelector;
	private final AiValidationSelector aiSelector;
	private final ValidationPlanComparator comparator;
	private final TestCatalogService testCatalogService;
	private volatile AuditService audit;

	public ValidationPlanService(ChangeAnalyzer analyzer, LocalValidationSelector localSelector,
			AiValidationSelector aiSelector, ValidationPlanComparator comparator,
			TestCatalogService testCatalogService) {
		this.analyzer = analyzer;
		this.localSelector = localSelector;
		this.aiSelector = aiSelector;
		this.comparator = comparator;
		this.testCatalogService = testCatalogService;
	}

	@Autowired(required = false)
	public void setAuditService(AuditService audit) {
		this.audit = audit;
	}

	public ValidationPlan generate(String taskId, String changeSetId, List<String> changedFiles,
			ValidationMode mode, String profile) {
		ValidationMode effectiveMode = mode == null ? ValidationMode.AUTO : mode;
		String effectiveProfile = profile == null || profile.isBlank() ? "TARGETED" : profile;
		ChangeAnalyzer.ChangeAnalysis analysis = analyzer.analyze(changedFiles);
		LocalPlan local = localSelector.select(analysis);

		boolean fallbackUsed = false;
		boolean aiInvoked = false;
		AiPlan aiPlan = null;
		List<String> catalog = List.of();
		List<String> aiSelectedTests = List.of();
		List<ValidationCheck> checks;
		List<String> disagreements = List.of();

		switch (effectiveMode) {
			case LOCAL -> checks = local.checks();
			case AI -> {
				aiInvoked = true;
				AiResult result = suggestAi(analysis, local, effectiveProfile);
				catalog = result.catalog();
				aiSelectedTests = result.aiSelectedTests();
				if (result.aiPlan() == null) {
					fallbackUsed = true;
					checks = broaderFallback(local, analysis);
				}
				else {
					aiPlan = result.aiPlan();
					ValidationPlanComparator.MergeResult merged =
						comparator.merge(local, result.aiPlan());
					checks = merged.checks();
					disagreements = merged.disagreements();
				}
			}
			case VERIFY -> {
				aiInvoked = true;
				AiResult result = suggestAi(analysis, local, effectiveProfile);
				catalog = result.catalog();
				aiSelectedTests = result.aiSelectedTests();
				if (result.aiPlan() == null) {
					fallbackUsed = true;
					checks = broaderFallback(local, analysis);
					disagreements = List.of("AI unavailable; local broader validation used");
				}
				else {
					aiPlan = result.aiPlan();
					ValidationPlanComparator.MergeResult merged =
						comparator.merge(local, result.aiPlan());
					checks = merged.checks();
					disagreements = merged.disagreements();
				}
			}
			default -> { // AUTO
				if (local.confidence() == ConfidenceLevel.HIGH) {
					checks = local.checks(); // 高置信度不调 AI
				}
				else {
					aiInvoked = true;
					AiResult result = suggestAi(analysis, local, effectiveProfile);
					catalog = result.catalog();
					aiSelectedTests = result.aiSelectedTests();
					if (result.aiPlan() == null) {
						fallbackUsed = true;
						checks = broaderFallback(local, analysis);
					}
					else {
						aiPlan = result.aiPlan();
						ValidationPlanComparator.MergeResult merged =
							comparator.merge(local, result.aiPlan());
						checks = merged.checks();
						disagreements = merged.disagreements();
					}
				}
			}
		}

		recordObservability(taskId, effectiveMode, analysis, local, aiInvoked, fallbackUsed,
			checks, disagreements, catalog, aiSelectedTests);
		return new ValidationPlan(taskId, changeSetId, effectiveMode, effectiveProfile,
			analysis.risk(), analysis.confidence(), List.copyOf(checks), local, aiPlan,
			disagreements, fallbackUsed, Instant.now());
	}

	private record AiResult(AiPlan aiPlan, List<String> catalog,
			List<String> aiSelectedTests) {

		private static AiResult empty() {
			return new AiResult(null, List.of(), List.of());
		}
	}

	private AiResult suggestAi(ChangeAnalyzer.ChangeAnalysis analysis, LocalPlan local,
			String profile) {
		if (aiSelector == null || !aiSelector.isAvailable()) {
			return AiResult.empty();
		}
		List<String> catalog = testCatalogService == null ? List.of()
			: testCatalogService.scan(analysis.workingDirectory()).stream()
				.map(TestCatalogService.CatalogTest::testId)
				.toList();
		try {
			AiValidationSelector.AiValidationInput input =
				new AiValidationSelector.AiValidationInput(null, analysis.changedFiles(),
					null, analysis.module(), analysis.candidateTests(), catalog,
					analysis.risk(), analysis.confidence());
			AiPlan plan = aiSelector.suggest(input);
			List<String> aiSelectedTests = plan.checks().stream()
				.filter(check -> check.type() == CheckType.MAVEN_TARGETED_TEST
					|| check.type() == CheckType.FRONTEND_TARGETED_TEST)
				.map(check -> check.arguments().isEmpty() ? ""
					: check.arguments().get(check.arguments().size() - 1))
				.filter(arg -> !arg.isBlank())
				.toList();
			return new AiResult(plan, catalog, aiSelectedTests);
		}
		catch (RuntimeException exception) {
			// timeout / rate limit / unavailable / auth / 非法输出 → fallback，不允许 Planning 失败
			return new AiResult(null, catalog, List.of());
		}
	}

	/** AI 不可用 fallback：在 local 基础上扩大为 broader/module validation。 */
	private List<ValidationCheck> broaderFallback(LocalPlan local,
			ChangeAnalyzer.ChangeAnalysis analysis) {
		List<ValidationCheck> checks = new ArrayList<>(local.checks());
		boolean hasModuleTest = checks.stream()
			.anyMatch(check -> check.type() == CheckType.MAVEN_MODULE_TEST);
		boolean hasBuild = checks.stream()
			.anyMatch(check -> check.type() == CheckType.FRONTEND_BUILD);
		if (!hasModuleTest && ChangeAnalyzer.TOOLCHAIN_JAVA.equals(analysis.toolchain())) {
			checks.add(new ValidationCheck(CheckType.MAVEN_MODULE_TEST, "maven",
				analysis.workingDirectory(), List.of("test"), false,
				"AI unavailable: broader module validation fallback",
				CheckSource.MERGED, 600));
		}
		if (!hasBuild && ChangeAnalyzer.TOOLCHAIN_VUE_TS.equals(analysis.toolchain())) {
			checks.add(new ValidationCheck(CheckType.FRONTEND_BUILD, "npm",
				analysis.workingDirectory(), List.of("run", "build"), false,
				"AI unavailable: frontend build fallback", CheckSource.MERGED, 600));
		}
		return List.copyOf(checks);
	}

	/** 可观测性：mode / confidence / risk / aiInvoked / fallback / checks 数量 / disagreements。 */
	private void recordObservability(String taskId, ValidationMode mode,
			ChangeAnalyzer.ChangeAnalysis analysis, LocalPlan local, boolean aiInvoked,
			boolean fallbackUsed, List<ValidationCheck> checks, List<String> disagreements,
			List<String> catalog, List<String> aiSelectedTests) {
		if (audit == null) {
			return;
		}
		java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
		metadata.put("mode", mode.name());
		metadata.put("profile", analysis.module());
		metadata.put("confidence", String.valueOf(analysis.confidence()));
		metadata.put("risk", String.valueOf(analysis.risk()));
		metadata.put("aiInvoked", String.valueOf(aiInvoked));
		metadata.put("aiProvider", aiSelector == null ? "" : aiSelector.providerId());
		metadata.put("aiModel", aiSelector == null ? "" : aiSelector.modelId());
		metadata.put("fallbackUsed", String.valueOf(fallbackUsed));
		metadata.put("catalogSize", String.valueOf(catalog.size()));
		metadata.put("aiSelectedTests", String.join(",", aiSelectedTests));
		metadata.put("localChecks", String.valueOf(local.checks().size()));
		metadata.put("finalChecks", String.valueOf(checks.size()));
		metadata.put("disagreements", String.valueOf(disagreements.size()));
		audit.taskEvent(EventType.VALIDATION_PLAN_GENERATED, taskId, null, "PLANNED",
			"Validation plan generated: " + mode, metadata);
	}
}
