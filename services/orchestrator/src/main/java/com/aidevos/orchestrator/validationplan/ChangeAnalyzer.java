package com.aidevos.orchestrator.validationplan;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.RiskLevel;
import org.springframework.stereotype.Component;

/**
 * ChangeAnalyzer：根据 changed files 确定 project/module、toolchain、change type、
 * 候选测试与 workingDirectory（必须定位到含 pom.xml/package.json 的模块根，
 * 避免 Maven 在 repo root 找不到 pom.xml）。
 *
 * 纯确定性规则，不调用 LLM。
 */
@Component
public class ChangeAnalyzer {

	public static final String TOOLCHAIN_JAVA = "JAVA";
	public static final String TOOLCHAIN_VUE_TS = "VUE_TS";
	public static final String TOOLCHAIN_NONE = "NONE";

	public static final String TYPE_JAVA_PRODUCTION = "JAVA_PRODUCTION";
	public static final String TYPE_JAVA_TEST = "JAVA_TEST";
	public static final String TYPE_VUE_TS = "VUE_TS";
	public static final String TYPE_FRONTEND_TEST = "FRONTEND_TEST";
	public static final String TYPE_POM = "POM";
	public static final String TYPE_PACKAGE_JSON = "PACKAGE_JSON";
	public static final String TYPE_CONFIG_RESOURCES = "CONFIG_RESOURCES";
	public static final String TYPE_DOC_ONLY = "DOC_ONLY";
	public static final String TYPE_UNKNOWN = "UNKNOWN";

	public record ChangeAnalysis(String module, String workingDirectory, String toolchain,
			String changeType, RiskLevel risk, ConfidenceLevel confidence,
			List<String> candidateTests, List<String> changedFiles) {
	}

	public ChangeAnalysis analyze(List<String> changedFiles) {
		List<String> files = changedFiles == null ? List.of() : changedFiles;
		String workingDirectory = workingDirectoryOf(files);
		String module = moduleOf(workingDirectory);
		String toolchain = toolchainOf(files);
		String changeType = changeTypeOf(files);
		List<String> candidateTests = candidateTestsOf(files, workingDirectory);
		RiskLevel risk = riskOf(files, changeType);
		ConfidenceLevel confidence = confidenceOf(files, changeType, candidateTests);
		return new ChangeAnalysis(module, workingDirectory, toolchain, changeType, risk,
			confidence, candidateTests, List.copyOf(files));
	}

	// ==================== workingDirectory / module ====================

	private String workingDirectoryOf(List<String> files) {
		for (String file : files) {
			if (file == null) {
				continue;
			}
			if (file.startsWith("services/orchestrator/frontend/")) {
				return "services/orchestrator/frontend";
			}
			if (file.startsWith("services/")) {
				String[] segments = file.split("/");
				if (segments.length >= 2) {
					return "services/" + segments[1];
				}
			}
		}
		return ".";
	}

	private String moduleOf(String workingDirectory) {
		return ".".equals(workingDirectory) ? "ROOT" : workingDirectory;
	}

	// ==================== toolchain / change type ====================

	private String toolchainOf(List<String> files) {
		boolean java = false;
		boolean vueTs = false;
		for (String file : files) {
			if (file == null) {
				continue;
			}
			if (file.endsWith(".java") || file.endsWith("pom.xml")) {
				java = true;
			}
			if (file.endsWith(".vue") || file.endsWith(".ts") || file.endsWith(".tsx")
					|| file.endsWith(".js") || file.endsWith("package.json")) {
				vueTs = true;
			}
		}
		if (java && vueTs) {
			return TOOLCHAIN_JAVA + "+" + TOOLCHAIN_VUE_TS;
		}
		if (java) {
			return TOOLCHAIN_JAVA;
		}
		if (vueTs) {
			return TOOLCHAIN_VUE_TS;
		}
		return TOOLCHAIN_NONE;
	}

	private String changeTypeOf(List<String> files) {
		boolean docOnly = !files.isEmpty();
		String dominant = TYPE_UNKNOWN;
		for (String file : files) {
			if (file == null) {
				continue;
			}
			docOnly = docOnly && isDoc(file);
			String type = singleChangeType(file);
			if (TYPE_JAVA_PRODUCTION.equals(type) || TYPE_JAVA_TEST.equals(type)
					|| TYPE_VUE_TS.equals(type) || TYPE_FRONTEND_TEST.equals(type)
					|| TYPE_POM.equals(type) || TYPE_PACKAGE_JSON.equals(type)
					|| TYPE_CONFIG_RESOURCES.equals(type)) {
				dominant = type;
			}
		}
		return docOnly && !dominant.equals(TYPE_UNKNOWN) ? TYPE_DOC_ONLY : dominant;
	}

	private String singleChangeType(String file) {
		if (file.endsWith("pom.xml")) {
			return TYPE_POM;
		}
		if (file.endsWith("package.json")) {
			return TYPE_PACKAGE_JSON;
		}
		if (file.contains("/src/test/java/") && file.endsWith(".java")) {
			return TYPE_JAVA_TEST;
		}
		if (file.contains("/src/main/java/") && file.endsWith(".java")) {
			return TYPE_JAVA_PRODUCTION;
		}
		if (file.contains("/src/test/") && (file.endsWith(".test.ts") || file.endsWith(".spec.ts")
				|| file.endsWith(".test.tsx") || file.endsWith(".spec.js"))) {
			return TYPE_FRONTEND_TEST;
		}
		if (file.endsWith(".vue") || file.endsWith(".ts") || file.endsWith(".tsx")
				|| file.endsWith(".js")) {
			return TYPE_VUE_TS;
		}
		if (file.startsWith("configs/") || file.startsWith("infrastructure/")
				|| file.endsWith(".yml") || file.endsWith(".yaml") || file.endsWith(".properties")
				|| file.endsWith(".sql") || file.contains("/src/main/resources/")) {
			return TYPE_CONFIG_RESOURCES;
		}
		return TYPE_UNKNOWN;
	}

	private boolean isDoc(String file) {
		return file.startsWith("docs/") || file.endsWith(".md");
	}

	// ==================== candidate tests ====================

	private List<String> candidateTestsOf(List<String> files, String workingDirectory) {
		List<String> candidates = new ArrayList<>();
		for (String file : files) {
			if (file == null) {
				continue;
			}
			if (file.contains("/src/test/java/") && file.endsWith(".java")) {
				candidates.add(file); // changed test 本身
			}
			else if (file.contains("/src/main/java/") && file.endsWith(".java")) {
				String test = file.replace("/src/main/java/", "/src/test/java/")
					.replace(".java", "Test.java");
				candidates.add(test); // 同名测试
			}
			else if (file.endsWith(".test.ts") || file.endsWith(".spec.ts")
					|| file.endsWith(".test.tsx") || file.endsWith(".spec.js")) {
				candidates.add(file); // frontend test 本身
			}
			else if (file.endsWith(".vue")) {
				String base = file.substring(0, file.length() - 4);
				candidates.add(base + ".spec.ts");
				candidates.add(base + ".test.ts");
			}
		}
		return List.copyOf(candidates);
	}

	// ==================== risk / confidence（确定性） ====================

	private RiskLevel riskOf(List<String> files, String changeType) {
		if (TYPE_DOC_ONLY.equals(changeType)) {
			return RiskLevel.LOW;
		}
		if (TYPE_POM.equals(changeType) || TYPE_PACKAGE_JSON.equals(changeType)
				|| containsSharedInfra(files)) {
			return RiskLevel.HIGH;
		}
		if (TYPE_JAVA_TEST.equals(changeType) || TYPE_FRONTEND_TEST.equals(changeType)) {
			return RiskLevel.LOW;
		}
		if (files.size() > 3) {
			return RiskLevel.HIGH;
		}
		if (files.size() > 1) {
			return RiskLevel.MEDIUM;
		}
		return RiskLevel.MEDIUM;
	}

	private boolean containsSharedInfra(List<String> files) {
		for (String file : files) {
			if (file != null && (file.contains("persistence") || file.contains("scheduler")
					|| file.contains("infrastructure") || file.startsWith("infrastructure/"))) {
				return true;
			}
		}
		return false;
	}

	private ConfidenceLevel confidenceOf(List<String> files, String changeType,
			List<String> candidateTests) {
		if (TYPE_DOC_ONLY.equals(changeType)) {
			return ConfidenceLevel.HIGH;
		}
		if (TYPE_POM.equals(changeType) || TYPE_PACKAGE_JSON.equals(changeType)) {
			return ConfidenceLevel.MEDIUM;
		}
		if (TYPE_JAVA_TEST.equals(changeType) && files.size() == 1) {
			return ConfidenceLevel.HIGH;
		}
		// 单个 production + 同名测试可达 → HIGH
		if (files.size() == 1 && !candidateTests.isEmpty()
				&& TYPE_JAVA_PRODUCTION.equals(changeType)) {
			return ConfidenceLevel.HIGH;
		}
		if (files.size() > 3 || TYPE_UNKNOWN.equals(changeType)) {
			return ConfidenceLevel.LOW;
		}
		if (candidateTests.isEmpty()) {
			return ConfidenceLevel.LOW;
		}
		return ConfidenceLevel.MEDIUM;
	}
}
