package com.aidevos.orchestrator.recovery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import com.aidevos.orchestrator.delivery.DeliveryStatus;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosis;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosisService;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.schedule.PlanScheduler;
import com.aidevos.orchestrator.recovery.RecoveryAttempt.AttemptStatus;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.validation.ValidationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * V1 统一 Recovery 入口：Diagnosis → Decision → Budget → 最小范围 Recovery → Audit。
 *
 * - 不复制任何底层状态机：只调用现有 authority
 * - 不阻塞服务线程做 backoff（记录 nextEligibleAt，由下一次 evaluate 生效）
 * - 同一个 taskId + fingerprint + action 共享 attempt budget，重启不重置
 */
@Service
public class RecoveryCoordinator {

	private final RecoveryPolicy policy;
	private final RecoveryAttemptRepository attempts;
	private final FailureDiagnosisService diagnosisService;
	private final AuditService auditService;
	private volatile TaskCenterService taskCenterService;
	private volatile DeliveryPipelineService deliveryPipelineService;
	private volatile PlanScheduler planScheduler;
	private volatile ValidationService validationService;
	private volatile ChangeService changeService;
	private volatile ExecutionEngine executionEngine;
	/** RETRY_EXECUTION 需要 TaskDefinition：解析器未注入时无安全入口 → 标记失败转人工。 */
	private volatile Function<String, TaskDefinition> taskDefinitionResolver;

	public RecoveryCoordinator(RecoveryPolicy policy, RecoveryAttemptRepository attempts,
			FailureDiagnosisService diagnosisService, AuditService auditService) {
		this.policy = policy;
		this.attempts = attempts;
		this.diagnosisService = diagnosisService;
		this.auditService = auditService;
	}

	@Autowired(required = false)
	public void setTaskCenterService(TaskCenterService value) { this.taskCenterService = value; }

	@Autowired(required = false)
	public void setDeliveryPipelineService(DeliveryPipelineService value) {
		this.deliveryPipelineService = value;
	}

	@Autowired(required = false)
	public void setPlanScheduler(PlanScheduler value) { this.planScheduler = value; }

	@Autowired(required = false)
	public void setValidationService(ValidationService value) { this.validationService = value; }

	@Autowired(required = false)
	public void setChangeService(ChangeService value) { this.changeService = value; }

	@Autowired(required = false)
	public void setExecutionEngine(ExecutionEngine value) { this.executionEngine = value; }

	public void setTaskDefinitionResolverForTests(Function<String, TaskDefinition> resolver) {
		this.taskDefinitionResolver = resolver;
	}

	/** 生产装配：注入 ExecutionTaskDefinitionResolver（从 job snapshot 重建 TaskDefinition）。 */
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	public void setTaskDefinitionResolver(ExecutionTaskDefinitionResolver resolver) {
		this.taskDefinitionResolver = resolver == null ? null : resolver::apply;
	}

	// ==================== Decision ====================

	public RecoveryDecision decide(String taskId) {
		return decide(diagnosisService.diagnose(taskId));
	}

	public RecoveryDecision decide(FailureDiagnosis diagnosis) {
		int used = usedAutomaticAttempts(diagnosis.taskId(), diagnosis.fingerprint(),
			policy.entryFor(diagnosis).action());
		int maxAttempts = Math.max(1, policy.entryFor(diagnosis).maxAttempts());
		RecoveryDecision decision = policy.decide(diagnosis,
			Math.min(used, maxAttempts), maxAttempts);
		auditService.taskEvent(EventType.RECOVERY_DECIDED, diagnosis.taskId(), null, null,
			"Recovery decision: " + decision.action(),
			Map.of("fingerprint", decision.diagnosisFingerprint(),
				"action", decision.action().name(),
				"automatic", decision.automaticAllowed(),
				"attempt", decision.attempt(), "maxAttempts", decision.maxAttempts(),
				"decisionSource", decision.decisionSource()));
		return decision;
	}

	/**
	 * 自动触发 hook（RECOVERY-AUTO-TRIGGER-CLOSEOUT）：
	 * 由失败 authority（ExecutionEngine / DeliveryPipelineService.fail）在失败持久化后调用。
	 * Recovery 自身异常只写 audit，绝不覆盖原始 failure。
	 */
	public void onFailure(String taskId) {
		try {
			evaluate(taskId);
		}
		catch (RuntimeException exception) {
			auditService.taskEvent(EventType.RECOVERY_FAILED, taskId, null, null,
				"Automatic recovery evaluation failed: " + exception.getMessage(),
				Map.of("automatic", true));
		}
	}

	// ==================== Evaluate / Execute ====================

	/**
	 * 统一恢复入口：诊断 → 决策 → 预算 → 执行（仅 automaticAllowed 且 budget 可用）。
	 * 返回 null 表示无需/不允许自动 recovery（WAITING_APPROVAL、终态、人工介入）。
	 */
	public RecoveryAttempt evaluate(String taskId) {
		if (isTerminalOrWaiting(taskId)) {
			return null;
		}
		FailureDiagnosis diagnosis = diagnosisService.diagnose(taskId);
		RecoveryDecision decision = decide(diagnosis);
		if (!decision.automaticAllowed()) {
			auditService.taskEvent(EventType.RECOVERY_HUMAN_REQUIRED, taskId, null, null,
				"Recovery requires human intervention: " + decision.action(),
				Map.of("fingerprint", decision.diagnosisFingerprint(),
					"action", decision.action().name(),
					"reason", decision.reason()));
			return null;
		}
		RecoveryAction action = decision.action();
		List<RecoveryAttempt> prior = attempts.findByFingerprint(taskId,
			decision.diagnosisFingerprint(), action);
		if (prior.size() >= decision.maxAttempts()) {
			RecoveryAttempt exhausted = new RecoveryAttempt("recovery-" + UUID.randomUUID(),
				taskId, decision.diagnosisFingerprint(), action, null,
				decision.maxAttempts(), decision.maxAttempts(), true,
				AttemptStatus.EXHAUSTED, Instant.now(), Instant.now(), "EXHAUSTED",
				"Automatic attempt budget exhausted (" + decision.maxAttempts() + "/"
					+ decision.maxAttempts() + "); human intervention required",
				decision.backoffSeconds());
			attempts.save(exhausted);
			auditService.taskEvent(EventType.RECOVERY_EXHAUSTED, taskId, null, null,
				"Recovery budget exhausted for " + action,
				Map.of("fingerprint", decision.diagnosisFingerprint(),
					"action", action.name(), "attempt", decision.maxAttempts(),
					"maxAttempts", decision.maxAttempts()));
			return exhausted;
		}
		// 递归/并发保护：同一 fingerprint+action 已有进行中的 recovery → 不重复执行
		if (attempts.hasRunning(taskId, decision.diagnosisFingerprint(), action)) {
			return null;
		}
		// Backoff：最近一次尝试尚未到 nextEligibleAt → 本次不执行（不 sleep）
		RecoveryAttempt latest = prior.isEmpty() ? null
			: prior.get(prior.size() - 1);
		if (latest != null && latest.finishedAt() != null
				&& latest.backoffSeconds() > 0) {
			Instant nextEligible = latest.finishedAt()
				.plusSeconds(latest.backoffSeconds());
			if (Instant.now().isBefore(nextEligible)) {
				return null;
			}
		}
		return execute(taskId, decision);
	}

	private RecoveryAttempt execute(String taskId, RecoveryDecision decision) {
		int attemptNumber = decision.attempt() + 1;
		RecoveryAttempt running = new RecoveryAttempt("recovery-" + UUID.randomUUID(),
			taskId, decision.diagnosisFingerprint(), decision.action(), null,
			attemptNumber, decision.maxAttempts(), true, AttemptStatus.RUNNING,
			Instant.now(), null, null, null, decision.backoffSeconds());
		attempts.save(running);
		auditService.taskEvent(EventType.RECOVERY_STARTED, taskId, null, null,
			"Recovery started: " + decision.action(),
			Map.of("fingerprint", decision.diagnosisFingerprint(),
				"action", decision.action().name(),
				"attempt", attemptNumber, "maxAttempts", decision.maxAttempts(),
				"automatic", true));
		Outcome outcome = executeAction(taskId, decision.action());
		AttemptStatus status = outcome.success() ? AttemptStatus.SUCCEEDED
			: AttemptStatus.FAILED;
		RecoveryAttempt finished = new RecoveryAttempt(running.attemptId(), taskId,
			decision.diagnosisFingerprint(), decision.action(), outcome.scopeId(),
			attemptNumber, decision.maxAttempts(), true, status, running.startedAt(),
			Instant.now(), outcome.result(), outcome.failureReason(),
			decision.backoffSeconds());
		attempts.save(finished);
		auditService.taskEvent(status == AttemptStatus.SUCCEEDED
			? EventType.RECOVERY_SUCCEEDED : EventType.RECOVERY_FAILED, taskId, null,
			null, "Recovery " + status + ": " + decision.action(),
			Map.of("fingerprint", decision.diagnosisFingerprint(),
				"action", decision.action().name(),
				"attempt", attemptNumber, "maxAttempts", decision.maxAttempts(),
				"automatic", true,
				"reason", outcome.failureReason() == null ? "" : outcome.failureReason()));
		return finished;
	}

	/** 只调用现有 authority；scope 无安全入口 → 标记失败（转人工），不改底层状态字段。 */
	private Outcome executeAction(String taskId, RecoveryAction action) {
		return switch (action) {
			case RETRY_EXECUTION -> retryExecution(taskId);
			case RETRY_DELIVERY -> retryDelivery(taskId);
			case RETRY_VALIDATION -> retryValidation(taskId);
			case RETRY_STEP -> retryStep(taskId);
			case REPLAN -> replan(taskId);
			case ABORT -> abort(taskId);
			case HUMAN_INTERVENTION -> new Outcome(false, null, "Human intervention required");
		};
	}

	private Outcome retryExecution(String taskId) {
		if (executionEngine == null || taskDefinitionResolver == null) {
			return new Outcome(false, null,
				"No execution authority available; manual retry required");
		}
		try {
			TaskDefinition definition = taskDefinitionResolver.apply(taskId);
			if (definition == null) {
				return new Outcome(false, null,
					"No task definition resolvable for execution retry");
			}
			ExecutionResult result = executionEngine.execute(definition);
			boolean success = result != null && result.isSuccess();
			return new Outcome(success, null,
				success ? "Execution retry succeeded" : "Execution retry failed: "
					+ (result == null ? "null result" : result.getMessage()));
		}
		catch (RuntimeException exception) {
			return new Outcome(false, null,
				"Execution retry failed: " + exception.getMessage());
		}
	}

	private Outcome retryDelivery(String taskId) {
		if (deliveryPipelineService == null) {
			return new Outcome(false, null,
				"No delivery authority available; manual retry required");
		}
		try {
			// FAILED pipeline 先走正式恢复入口（resumeFromFailure），否则 reconcile 短路永远无法自恢复
			DeliveryPipeline current = deliveryPipelineService.get(taskId);
			if (current != null && current.getStatus() == DeliveryStatus.FAILED) {
				current.resumeFromFailure();
			}
			DeliveryPipeline pipeline = deliveryPipelineService.advance(taskId);
			boolean success = pipeline != null
				&& pipeline.getStatus() != DeliveryStatus.FAILED;
			return new Outcome(success, taskId,
				success ? "Delivery advanced (stage=" + (pipeline == null ? "?" : pipeline.getCurrentStage()) + ")"
					: "Delivery retry failed: " + (pipeline == null ? "null pipeline"
						: pipeline.getFailureReason()));
		}
		catch (RuntimeException exception) {
			return new Outcome(false, taskId,
				"Delivery retry failed: " + exception.getMessage());
		}
	}

	private Outcome retryValidation(String taskId) {
		if (validationService == null || changeService == null) {
			return new Outcome(false, null,
				"No validation authority available; manual retry required");
		}
		try {
			ChangeSet change = latestChange(taskId);
			if (change == null) {
				return new Outcome(false, null,
					"No change set found for validation retry");
			}
			ValidationRun run = validationService.startDelivery(change.getChangeId());
			boolean success = run.getStatus() == ValidationStatus.SUCCESS;
			return new Outcome(success, run.getValidationRunId(),
				success ? "Validation retry succeeded"
					: "Validation retry failed: " + (run.getSummary() == null ? ""
						: run.getSummary()));
		}
		catch (RuntimeException exception) {
			return new Outcome(false, null,
				"Validation retry failed: " + exception.getMessage());
		}
	}

	private Outcome retryStep(String taskId) {
		return new Outcome(false, null,
			"Plan step retry requires explicit plan run scope; manual intervention");
	}

	private Outcome replan(String taskId) {
		return new Outcome(false, null,
			"Replan requires explicit plan run scope; manual intervention");
	}

	private Outcome abort(String taskId) {
		if (taskCenterService == null) {
			return new Outcome(false, null, "No task authority available");
		}
		try {
			taskCenterService.cancel(taskId);
			return new Outcome(true, taskId, "Task aborted");
		}
		catch (RuntimeException exception) {
			return new Outcome(false, taskId, "Abort failed: " + exception.getMessage());
		}
	}

	// ==================== helpers ====================

	private ChangeSet latestChange(String taskId) {
		if (changeService == null) {
			return null;
		}
		List<ChangeSet> changes = changeService.getChangesByTask(taskId);
		return changes.isEmpty() ? null : changes.get(changes.size() - 1);
	}

	private int usedAutomaticAttempts(String taskId, String fingerprint,
			RecoveryAction action) {
		return attempts.findByFingerprint(taskId, fingerprint, action).size();
	}

	private boolean isTerminalOrWaiting(String taskId) {
		if (taskCenterService != null) {
			TaskRecord task = taskCenterService.getTask(taskId).orElse(null);
			if (task != null && (task.getStatus() == TaskStatus.SUCCESS
					|| task.getStatus() == TaskStatus.COMPLETED
					|| task.getStatus() == TaskStatus.FAILED
					|| task.getStatus() == TaskStatus.CANCELLED
					|| task.getStatus() == TaskStatus.REJECTED)) {
				return true;
			}
		}
		// WAITING_APPROVAL 不是 Failure：不生成自动 recovery
		if (deliveryPipelineService != null) {
			DeliveryPipeline pipeline = deliveryPipelineService.get(taskId);
			if (pipeline != null
					&& pipeline.getStatus() == DeliveryStatus.WAITING_APPROVAL) {
				return true;
			}
		}
		return false;
	}

	private record Outcome(boolean success, String scopeId, String failureReason) {

		String result() {
			return success ? "SUCCEEDED" : "FAILED";
		}
	}
}
