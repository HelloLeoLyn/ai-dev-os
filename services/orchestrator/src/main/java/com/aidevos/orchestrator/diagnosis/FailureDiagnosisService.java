package com.aidevos.orchestrator.diagnosis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryStage;
import com.aidevos.orchestrator.delivery.DeliveryStatus;
import com.aidevos.orchestrator.execution.query.ExecutionRecordDetail;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.springframework.stereotype.Service;

/**
 * V1 Deterministic Failure Diagnosis Engine。
 *
 * 确定性规则优先，不调用 LLM；无法确定时给 UNKNOWN（仍带 stage + 原始 errorCode + 关键 evidence）。
 * 正常人工 Gate（WAITING_APPROVAL 等）不产生 diagnosis。
 */
@Service
public class FailureDiagnosisService {

	private final FailureEvidenceCollector collector;

	public FailureDiagnosisService(FailureEvidenceCollector collector) {
		this.collector = collector;
	}

	public FailureDiagnosis diagnose(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return null;
		}
		return diagnose(collector.collect(taskId));
	}

	public FailureDiagnosis diagnose(TaskFailureEvidence evidence) {
		if (evidence == null) {
			return null;
		}
		// 阶段 H：正常人工 Gate / 等待审批 → 不是失败，不伪造 diagnosis
		if (isWaitingApproval(evidence)) {
			return null;
		}
		// DeliveryPipeline FAILED：最具体，优先（必须指出具体 stage + failureReason）
		if (evidence.pipeline() != null && evidence.pipeline().getStatus() == DeliveryStatus.FAILED) {
			FailureDiagnosis delivery = diagnoseDelivery(evidence);
			if (delivery != null) {
				return delivery;
			}
		}
		if (evidence.failedExecution() != null) {
			FailureDiagnosis execution = diagnoseExecution(evidence);
			if (execution != null) {
				return execution;
			}
		}
		if (evidence.task() != null && evidence.task().getStatus() == TaskStatus.FAILED) {
			return unknown(evidence);
		}
		return null; // no active failure
	}

	// ==================== 阶段 H：正常 Gate 不误报 ====================

	private boolean isWaitingApproval(TaskFailureEvidence evidence) {
		if (evidence.pipeline() != null
				&& evidence.pipeline().getStatus() == DeliveryStatus.WAITING_APPROVAL) {
			return true;
		}
		if (evidence.failedExecution() != null) {
			return false;
		}
		return false;
	}

	// ==================== Delivery 规则 ====================

	private FailureDiagnosis diagnoseDelivery(TaskFailureEvidence evidence) {
		DeliveryPipeline pipeline = evidence.pipeline();
		String failureReason = pipeline.getFailureReason() == null ? "" : pipeline.getFailureReason();
		String stage = deliveryStageLabel(pipeline.getCurrentStage());
		List<String> evidenceItems = new ArrayList<>();
		evidenceItems.add("stage=" + stage);
		evidenceItems.add("failureReason=" + FailureEvidenceCollector.snippet(failureReason));
		if (pipeline.getFailureClass() != null) {
			evidenceItems.add("failureClass=" + pipeline.getFailureClass());
		}

		// 规则 4：PullRequest not found → PERSISTED_REFERENCE_NOT_RESOLVABLE（重启悬空引用）
		if (containsAny(failureReason, "PullRequest not found", "pull request not found")) {
			return build(evidence.task() == null ? null : evidence.task().getTaskId(),
				"DELIVERY", "CI", null, "CI_CHECK_FAILED", "PERSISTED_REFERENCE_NOT_RESOLVABLE",
				FailureCategory.DELIVERY,
				"CI check failed: a persisted delivery reference cannot be resolved",
				"DeliveryPipeline 引用的实体（如 PullRequest）在重启后无法解析，通常因为该实体未被持久化或已被清除。",
				evidenceItems, RecommendedAction.RETRY, true,
				fingerprint("DELIVERY", "PERSISTED_REFERENCE_NOT_RESOLVABLE",
					"persisted reference not resolvable", "ci-check"));
		}
		// 规则 7：Validation failed → VALIDATION_FAILED
		if (containsAny(failureReason, "Validation failed", "validation failed")) {
			return build(evidence.task() == null ? null : evidence.task().getTaskId(),
				"VALIDATION", "Validation", null, "VALIDATION_FAILED", "VALIDATION_FAILED",
				FailureCategory.VALIDATION,
				"Delivery validation failed",
				"Validation 未通过（代码/环境/测试原因），Delivery 停在 VALIDATING 后失败。",
				evidenceItems, RecommendedAction.RETRY, true,
				fingerprint("VALIDATION", "VALIDATION_FAILED", "validation failed", "delivery-validation"));
		}
		// 规则 8：其他 Delivery FAILED → 具体 stage + failureReason（不显示裸 DELIVERY_FAILED）
		return build(evidence.task() == null ? null : evidence.task().getTaskId(),
			"DELIVERY", stage, null, pipeline.getFailureClass() == null
				? "DELIVERY_FAILED" : pipeline.getFailureClass().name(),
			"DELIVERY_STAGE_FAILED", FailureCategory.DELIVERY,
			"Delivery failed at " + stage,
			failureReason.isBlank() ? "Delivery stage " + stage + " failed." : failureReason,
			evidenceItems, RecommendedAction.RETRY, true,
			fingerprint("DELIVERY", "DELIVERY_STAGE_FAILED", failureReason, stage));
	}

	// ==================== Execution 规则 ====================

	private FailureDiagnosis diagnoseExecution(TaskFailureEvidence evidence) {
		ExecutionRecordDetail execution = evidence.failedExecution();
		String taskId = evidence.task() == null ? null : evidence.task().getTaskId();
		String errorCode = execution.errorCode() == null ? "" : execution.errorCode();
		String errorMessage = execution.errorMessage() == null ? "" : execution.errorMessage();
		String message = execution.message() == null ? "" : execution.message();
		String output = execution.output() == null ? "" : execution.output();
		String combined = errorCode + " " + errorMessage + " " + message + " " + output;
		String stepId = execution.stepRunId();
		List<String> evidenceItems = new ArrayList<>();
		if (execution.workspace() != null) {
			evidenceItems.add("workingDirectory=" + execution.workspace());
		}
		if (execution.exitCode() != null) {
			evidenceItems.add("exitCode=" + execution.exitCode());
		}
		if (execution.errorCode() != null) {
			evidenceItems.add("errorCode=" + execution.errorCode());
		}

		// 规则 1：Maven no POM → WRONG_WORKING_DIRECTORY
		if ((containsAny(errorCode, "BUILD_FAILED", "BUILD FAILURE")
				|| containsAny(combined, "BUILD FAILURE", "build failed"))
				&& containsAny(combined.toLowerCase(), "pom", "no pom", "pom.xml")) {
			evidenceItems.add("pom.xml not found");
			return build(taskId, "EXECUTION", "Execution/Maven", stepId, "BUILD_FAILED",
				"WRONG_WORKING_DIRECTORY", FailureCategory.CONFIGURATION,
				"Build failed: working directory has no pom.xml",
				"Maven 在缺少 pom.xml 的目录执行（工作目录错误或未检出源码）。",
				evidenceItems, RecommendedAction.RETRY, true,
				fingerprint("CONFIGURATION", "WRONG_WORKING_DIRECTORY",
					"maven working directory without pom", "build"));
		}
		// 规则 2：MODEL_NOT_FOUND + agent default missing → AGENT_DEFAULT_MODEL_MISSING
		if (containsAny(errorCode, "MODEL_NOT_FOUND", "Model resolution failed")
				|| containsAny(combined, "No model requested and the agent has no default model")) {
			evidenceItems.add("requestedModelId=" + (execution.requestedModelId() == null
				? "" : execution.requestedModelId()));
			return build(taskId, "EXECUTION", "Model Resolution", stepId, "MODEL_NOT_FOUND",
				"AGENT_DEFAULT_MODEL_MISSING", FailureCategory.MODEL,
				"Model resolution failed: no model requested and no agent default model",
				"Agent 未配置默认模型（agents.yaml executorConfig.model 为空）且 Task 未传 requestedModelId。",
				evidenceItems, RecommendedAction.FIX_CONFIGURATION, true,
				fingerprint("MODEL", "AGENT_DEFAULT_MODEL_MISSING",
					"no model requested and no agent default", "model-resolution"));
		}
		// 规则 3：MODE_CONFLICT → TASK_MODE_CONFLICT
		if (containsAny(combined, "MODE_CONFLICT")) {
			return build(taskId, "EXECUTION", "Preflight", stepId, "MODE_CONFLICT",
				"TASK_MODE_CONFLICT", FailureCategory.CONFIGURATION,
				"Task mode conflicts with the work required",
				"READ_ONLY 任务收到需要写代码的目标（CODE_GENERATION），应在进入执行前改为 READ_WRITE。",
				evidenceItems, RecommendedAction.FIX_CONFIGURATION, true,
				fingerprint("CONFIGURATION", "TASK_MODE_CONFLICT",
					"read-only task requires write", "preflight"));
		}
		// 规则 5：Bean 冲突 → SPRING_BEAN_CONFLICT
		if (containsAny(combined, "expected single matching bean but found",
				"expected single bean but found", "required a single bean")) {
			return build(taskId, "SYSTEM", "Startup", stepId, "SPRING_BEAN_CONFLICT",
				"SPRING_BEAN_CONFLICT", FailureCategory.CONFIGURATION,
				"Spring context failed: multiple beans of the same type",
				"同一接口存在多个候选 Bean（条件装配未互斥），需检查 @ConditionalOnProperty 配置。",
				evidenceItems, RecommendedAction.FIX_CONFIGURATION, true,
				fingerprint("CONFIGURATION", "SPRING_BEAN_CONFLICT",
					"expected single bean but found", "spring-context"));
		}
		// 无法确定 → UNKNOWN（仍给 stage + errorCode + 关键 evidence）
		return unknown(evidence);
	}

	// ==================== UNKNOWN ====================

	private FailureDiagnosis unknown(TaskFailureEvidence evidence) {
		TaskStatus taskStatus = evidence.task() == null ? null : evidence.task().getStatus();
		ExecutionRecordDetail execution = evidence.failedExecution();
		String taskId = evidence.task() == null ? null : evidence.task().getTaskId();
		String rawCode = execution != null && execution.errorCode() != null
			? execution.errorCode() : taskStatus == null ? "UNKNOWN" : taskStatus.name();
		List<String> evidenceItems = new ArrayList<>();
		if (evidence.task() != null && evidence.task().getErrorMessage() != null) {
			evidenceItems.add("taskError=" + FailureEvidenceCollector.snippet(
				evidence.task().getErrorMessage()));
		}
		if (execution != null) {
			if (execution.errorMessage() != null) {
				evidenceItems.add("errorMessage=" + FailureEvidenceCollector.snippet(
					execution.errorMessage()));
			}
			if (execution.message() != null) {
				evidenceItems.add("message=" + FailureEvidenceCollector.snippet(execution.message()));
			}
			if (execution.exitCode() != null) {
				evidenceItems.add("exitCode=" + execution.exitCode());
			}
		}
		return build(taskId, execution == null ? "SYSTEM" : "EXECUTION",
			execution == null ? "Execution" : stageOf(execution), execution == null ? null
				: execution.stepRunId(),
			rawCode, "UNKNOWN", FailureCategory.UNKNOWN,
			"Failure detected but not yet classified",
			"未能匹配已知确定性规则；请结合证据与 timeline 人工判断。",
			evidenceItems, RecommendedAction.HUMAN_INTERVENTION, false,
			fingerprint("UNKNOWN", rawCode, "unclassified", "unknown"));
	}

	private String stageOf(ExecutionRecordDetail execution) {
		String type = execution.executionType() == null ? "" : execution.executionType();
		return type.isBlank() ? "Execution" : type;
	}

	// ==================== fingerprint（E） ====================

	/**
	 * 稳定 fingerprint：category + code + normalized rootCause + failedOperation。
	 * 归一化排除 taskId / UUID / 时间戳 / 绝对路径；同类错误重复出现时一致。
	 */
	public String fingerprint(String category, String code, String rootCause, String failedOperation) {
		String raw = category + "|" + code + "|" + normalize(rootCause)
			+ "|" + normalize(failedOperation);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++) {
				hex.append(String.format("%02x", hash[i]));
			}
			return hex.toString();
		}
		catch (Exception exception) {
			return Integer.toHexString(raw.hashCode());
		}
	}

	static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value
			.replaceAll("task-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "task-<id>")
			.replaceAll("[0-9a-f]{40}", "<sha>")
			.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<uuid>")
			.replaceAll("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|\\+\\d{2}:\\d{2})?", "<ts>")
			.replaceAll("/tmp/[^\\s\"']+", "/tmp/<path>")
			.replaceAll("/home/[^\\s\"']+", "/home/<path>")
			.replaceAll("\\s+", " ")
			.trim();
	}

	// ==================== helpers ====================

	private FailureDiagnosis build(String taskId, String source, String stage, String failedStepId,
			String errorCode, String code, FailureCategory category, String summary,
			String rootCause, List<String> evidence, RecommendedAction recommendedAction,
			boolean retryable, String fingerprint) {
		return new FailureDiagnosis(taskId, source, stage, failedStepId, errorCode, code,
			category, summary, rootCause, List.copyOf(evidence), recommendedAction,
			retryable, fingerprint, Instant.now());
	}

	private static boolean containsAny(String value, String... needles) {
		if (value == null) {
			return false;
		}
		for (String needle : needles) {
			if (value.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static String deliveryStageLabel(DeliveryStage stage) {
		if (stage == null) {
			return "DELIVERY";
		}
		return switch (stage) {
			case CHANGE_READY -> "Change";
			case VALIDATING -> "Validation";
			case QUALITY_GATE -> "Quality Gate";
			case COMMITTING -> "Commit";
			case WAITING_REMOTE_PUSH_APPROVAL -> "Remote Push Approval";
			case PUSHING -> "Push";
			case CREATING_PR -> "Pull Request";
			case CI_CHECKING -> "CI";
			case FAILED -> "FAILED";
			default -> stage.name();
		};
	}
}
