package com.aidevos.orchestrator.validationplan;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.LocalPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.RiskLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import org.springframework.stereotype.Component;

/**
 * LocalValidationSelector：纯确定性规则（0 LLM）。
 *
 * Mandatory Checks（AI 永远不能删除）：
 * - Java production: GIT_DIFF_CHECK + BACKEND_COMPILE
 * - Java test:       + 对应 targeted test
 * - Vue/TS:          GIT_DIFF_CHECK + FRONTEND_TYPECHECK
 * - package.json:    FRONTEND_TYPECHECK + FRONTEND_BUILD
 * - pom.xml:         BACKEND_COMPILE + module-level MAVEN_MODULE_TEST
 * - DOC_ONLY:        仅 GIT_DIFF_CHECK
 *
 * Targeted test 无法可靠定位时：confidence 降低并选择 broader/module 验证，
 * 绝不“猜一个测试”。
 */
@Component
public class LocalValidationSelector {

	private static final String TOOL_MAVEN = "maven";
	private static final String TOOL_GIT = "git";
	private static final String TOOL_NPM = "npm";

	public LocalPlan select(ChangeAnalyzer.ChangeAnalysis analysis) {
		List<ValidationCheck> checks = new ArrayList<>();
		String wd = analysis.workingDirectory();
		String changeType = analysis.changeType();

		// DOC_ONLY：只 diff，不跑 Maven/Frontend tests
		if (ChangeAnalyzer.TYPE_DOC_ONLY.equals(changeType)) {
			checks.add(check(CheckType.GIT_DIFF_CHECK, TOOL_GIT, wd, List.of("diff", "HEAD"),
				true, "Docs change: verify diff content only", CheckSource.MANDATORY, 60));
			return new LocalPlan(RiskLevel.LOW, ConfidenceLevel.HIGH, List.copyOf(checks));
		}

		// Java toolchain
		if (ChangeAnalyzer.TOOLCHAIN_JAVA.equals(analysis.toolchain())
				|| ChangeAnalyzer.TYPE_POM.equals(changeType)
				|| ChangeAnalyzer.TYPE_CONFIG_RESOURCES.equals(changeType)) {
			checks.add(check(CheckType.GIT_DIFF_CHECK, TOOL_GIT, wd, List.of("diff", "HEAD"),
				true, "Verify change scope", CheckSource.MANDATORY, 60));
			checks.add(check(CheckType.BACKEND_COMPILE, TOOL_MAVEN, wd, List.of("compile"),
				true, "Java change must compile", CheckSource.MANDATORY, 300));
			if (ChangeAnalyzer.TYPE_POM.equals(changeType)) {
				checks.add(check(CheckType.MAVEN_MODULE_TEST, TOOL_MAVEN, wd,
					List.of("test", "-pl", "."), true,
					"pom.xml change: module-level validation", CheckSource.MANDATORY, 600));
			}
			addJavaTests(checks, analysis);
		}

		// Vue/TS toolchain
		if (ChangeAnalyzer.TOOLCHAIN_VUE_TS.equals(analysis.toolchain())
				|| ChangeAnalyzer.TYPE_PACKAGE_JSON.equals(changeType)
				|| ChangeAnalyzer.TYPE_FRONTEND_TEST.equals(changeType)) {
			checks.add(check(CheckType.GIT_DIFF_CHECK, TOOL_GIT, wd, List.of("diff", "HEAD"),
				true, "Verify change scope", CheckSource.MANDATORY, 60));
			checks.add(check(CheckType.FRONTEND_TYPECHECK, TOOL_NPM, wd,
				List.of("run", "type-check", "--", "vue-tsc", "--noEmit"), true,
				"Vue/TS change must type-check", CheckSource.MANDATORY, 300));
			if (ChangeAnalyzer.TYPE_PACKAGE_JSON.equals(changeType)) {
				checks.add(check(CheckType.FRONTEND_BUILD, TOOL_NPM, wd,
					List.of("run", "build"), true,
					"package.json change: frontend build", CheckSource.MANDATORY, 600));
			}
			addFrontendTests(checks, analysis);
		}

		// 未识别 toolchain（如纯 resources 在非 java/vue 模块）→ 保守 diff
		if (checks.isEmpty()) {
			checks.add(check(CheckType.GIT_DIFF_CHECK, TOOL_GIT, wd, List.of("diff", "HEAD"),
				true, "Verify change scope", CheckSource.MANDATORY, 60));
		}

		return new LocalPlan(analysis.risk(), analysis.confidence(), List.copyOf(checks));
	}

	private void addJavaTests(List<ValidationCheck> checks,
			ChangeAnalyzer.ChangeAnalysis analysis) {
		// 同名测试可达且明确 → targeted test
		List<String> targeted = analysis.candidateTests().stream()
			.filter(test -> test.contains("/src/test/java/"))
			.toList();
		if (!targeted.isEmpty() && !ChangeAnalyzer.TYPE_POM.equals(analysis.changeType())) {
			for (String test : targeted) {
				checks.add(check(CheckType.MAVEN_TARGETED_TEST, TOOL_MAVEN,
					analysis.workingDirectory(), List.of("test", "-Dtest=" + testClassName(test)),
					!ChangeAnalyzer.TYPE_JAVA_TEST.equals(analysis.changeType()),
					"Targeted test for changed code: " + test, CheckSource.LOCAL, 300));
			}
			return;
		}
		// 无法可靠定位 targeted test → broader/module validation（不猜单个测试）
		if (ChangeAnalyzer.TYPE_JAVA_PRODUCTION.equals(analysis.changeType())
				|| ChangeAnalyzer.TYPE_CONFIG_RESOURCES.equals(analysis.changeType())) {
			checks.add(check(CheckType.MAVEN_MODULE_TEST, TOOL_MAVEN,
				analysis.workingDirectory(), List.of("test"), false,
				"Broader module test: targeted test not reliably locatable",
				CheckSource.LOCAL, 600));
		}
	}

	private void addFrontendTests(List<ValidationCheck> checks,
			ChangeAnalyzer.ChangeAnalysis analysis) {
		List<String> targeted = analysis.candidateTests().stream()
			.filter(test -> test.endsWith(".test.ts") || test.endsWith(".spec.ts")
				|| test.endsWith(".test.tsx") || test.endsWith(".spec.js"))
			.toList();
		if (!targeted.isEmpty() && !ChangeAnalyzer.TYPE_PACKAGE_JSON.equals(analysis.changeType())) {
			for (String test : targeted) {
				checks.add(check(CheckType.FRONTEND_TARGETED_TEST, TOOL_NPM,
					analysis.workingDirectory(), List.of("test", "--", test), false,
					"Targeted frontend test: " + test, CheckSource.LOCAL, 300));
			}
		}
	}

	private static String testClassName(String testPath) {
		String fileName = testPath.substring(testPath.lastIndexOf('/') + 1);
		return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
	}

	private static ValidationCheck check(CheckType type, String tool, String workingDirectory,
			List<String> arguments, boolean required, String reason, CheckSource source,
			int timeoutSeconds) {
		return new ValidationCheck(type, tool, workingDirectory, List.copyOf(arguments),
			required, reason, source, timeoutSeconds);
	}
}
