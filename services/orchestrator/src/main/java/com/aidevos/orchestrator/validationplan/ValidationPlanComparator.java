package com.aidevos.orchestrator.validationplan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.validationplan.ValidationPlanModels.AiPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckSource;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.CheckType;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ConfidenceLevel;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.LocalPlan;
import com.aidevos.orchestrator.validationplan.ValidationPlanModels.ValidationCheck;
import org.springframework.stereotype.Component;

/**
 * ValidationPlanComparator：Local 与 AI 计划的安全合并。
 *
 * 默认安全策略 UNION：
 * - mandatory checks 永远保留（AI 不得删除）
 * - local required checks 默认保留
 * - AI additional checks 加入 final plan
 * - 分歧过大 → disagreement=true + scope 扩大（升级 module-level），绝不缩小
 */
@Component
public class ValidationPlanComparator {

	public record MergeResult(List<ValidationCheck> checks, List<String> disagreements,
			boolean escalated) {
	}

	public MergeResult merge(LocalPlan local, AiPlan ai) {
		List<ValidationCheck> merged = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// 1. local checks（MANDATORY + LOCAL）全部保留
		for (ValidationCheck check : local.checks()) {
			merged.add(check);
			seen.add(key(check));
		}

		// 2. AI additional checks（source=AI）加入，去重；AI 不能产出 MANDATORY
		List<ValidationCheck> aiAdded = new ArrayList<>();
		if (ai != null && ai.checks() != null) {
			for (ValidationCheck check : ai.checks()) {
				if (check.source() == CheckSource.MANDATORY) {
					continue; // AI 无权声明 mandatory
				}
				if (seen.add(key(check))) {
					ValidationCheck asAi = new ValidationCheck(check.type(), check.tool(),
						check.workingDirectory(), check.arguments(), check.required(),
						check.reason(), CheckSource.AI, check.timeoutSeconds());
					merged.add(asAi);
					aiAdded.add(asAi);
				}
			}
		}

		List<String> disagreements = new ArrayList<>();
		boolean escalated = false;

		// 3. 分歧过大：AI 建议数量远超 local 且 AI confidence HIGH → scope 扩大
		int localCount = local.checks().size();
		if (ai != null && ai.checks() != null && ai.checks().size() >= 4
				&& ai.checks().size() >= localCount * 2
				&& ai.confidence() == ConfidenceLevel.HIGH) {
			escalated = true;
			disagreements.add("AI suggested " + ai.checks().size() + " checks vs local "
				+ localCount + "; escalating to broader module validation");
			String workingDirectory = firstWorkingDirectory(merged);
			ValidationCheck moduleTest = new ValidationCheck(CheckType.MAVEN_MODULE_TEST,
				"maven", workingDirectory, List.of("test"), false,
				"Scope escalated due to local/AI disagreement", CheckSource.MERGED, 600);
			if (seen.add(key(moduleTest))) {
				merged.add(moduleTest);
			}
		}
		// AI 增加量远超 local（但未达升级阈值）→ 记录分歧，仍取并集
		if (aiAdded.size() > localCount && !escalated) {
			disagreements.add("AI added " + aiAdded.size()
				+ " additional checks beyond local plan; union kept");
		}

		return new MergeResult(List.copyOf(merged), List.copyOf(disagreements), escalated);
	}

	private static String firstWorkingDirectory(List<ValidationCheck> checks) {
		for (ValidationCheck check : checks) {
			if (check.workingDirectory() != null && !check.workingDirectory().isBlank()) {
				return check.workingDirectory();
			}
		}
		return ".";
	}

	private static String key(ValidationCheck check) {
		return check.type() + "|" + check.workingDirectory() + "|" + check.arguments();
	}
}
