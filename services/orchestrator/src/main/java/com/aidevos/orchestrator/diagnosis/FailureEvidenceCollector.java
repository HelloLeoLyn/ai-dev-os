package com.aidevos.orchestrator.diagnosis;

import java.util.List;

import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import com.aidevos.orchestrator.execution.query.ExecutionRecordDetail;
import com.aidevos.orchestrator.execution.query.ExecutionRecordQueryService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Component;

/**
 * V1 Failure Evidence Collector：按 taskId 聚合现有事实。
 *
 * Task → PlanRun → 最新失败 ExecutionRecord（errorCode/exitCode/output 摘要）
 * → DeliveryPipeline（failureClass/failureReason/currentStage）。
 * 只取结构化字段与关键片段，不复制状态机。
 */
@Component
public class FailureEvidenceCollector {

	private static final int MAX_OUTPUT_CHARS = 500;

	private final TaskCenterService tasks;
	private final ExecutionRecordQueryService executions;
	private final DeliveryPipelineService delivery;
	private final PlanRunRepository planRuns;

	public FailureEvidenceCollector(TaskCenterService tasks,
			ExecutionRecordQueryService executions, DeliveryPipelineService delivery,
			PlanRunRepository planRuns) {
		this.tasks = tasks;
		this.executions = executions;
		this.delivery = delivery;
		this.planRuns = planRuns;
	}

	public TaskFailureEvidence collect(String taskId) {
		TaskRecord task = tasks.getTask(taskId).orElse(null);
		ExecutionRecordDetail failed = latestFailedExecution(taskId);
		DeliveryPipeline pipeline = taskId == null ? null : delivery.get(taskId);
		PlanRun planRun = null;
		if (task != null && task.getPlanRunId() != null && !task.getPlanRunId().isBlank()) {
			planRun = planRuns.get(task.getPlanRunId());
		}
		return new TaskFailureEvidence(task, failed, pipeline, planRun);
	}

	private ExecutionRecordDetail latestFailedExecution(String taskId) {
		if (taskId == null) {
			return null;
		}
		List<ExecutionRecordDetail> failed = executions.getAll("FAILED", taskId).stream()
			.map(record -> executions.get(record.id()).orElse(null))
			.filter(java.util.Objects::nonNull)
			.sorted(java.util.Comparator
				.comparing(ExecutionRecordDetail::completedAt,
					java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
				.reversed())
			.toList();
		return failed.isEmpty() ? null : failed.get(0);
	}

	/** 关键日志/输出片段截断（禁止整段日志进 diagnosis）。 */
	public static String snippet(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= MAX_OUTPUT_CHARS
			? trimmed : trimmed.substring(0, MAX_OUTPUT_CHARS) + "…";
	}
}
