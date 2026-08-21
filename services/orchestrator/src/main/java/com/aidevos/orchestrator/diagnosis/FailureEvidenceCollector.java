package com.aidevos.orchestrator.diagnosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import com.aidevos.orchestrator.execution.query.ExecutionRecordDetail;
import com.aidevos.orchestrator.execution.query.ExecutionRecordQueryService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.timeline.TimelineEventDTO;
import com.aidevos.orchestrator.timeline.TimelineService;
import org.springframework.stereotype.Component;

/**
 * V1 Failure Evidence Collector：按 taskId 聚合现有事实。
 *
 * Task → PlanRun → 最新失败 ExecutionRecord（errorCode/exitCode/output 摘要）
 * → DeliveryPipeline（failureClass/failureReason/currentStage）
 * → Timeline/Audit 关键失败事件（有限数量，去重 + snippet）。
 * 只取结构化字段与关键片段，不复制状态机。
 */
@Component
public class FailureEvidenceCollector {

	private static final int MAX_OUTPUT_CHARS = 500;
	private static final int MAX_TIMELINE_EVIDENCE = 5;
	private static final Set<String> FAILURE_EVENTS = Set.of(
		"STEP_FAILED", "EXECUTION_FAILED", "PLAN_RUN_FAILED",
		"DELIVERY_FAILED", "VALIDATION_FAILED", "TASK_FAILED", "CI_FAILED");

	private final TaskCenterService tasks;
	private final ExecutionRecordQueryService executions;
	private final DeliveryPipelineService delivery;
	private final PlanRunRepository planRuns;
	private final TimelineService timeline;

	public FailureEvidenceCollector(TaskCenterService tasks,
			ExecutionRecordQueryService executions, DeliveryPipelineService delivery,
			PlanRunRepository planRuns, TimelineService timeline) {
		this.tasks = tasks;
		this.executions = executions;
		this.delivery = delivery;
		this.planRuns = planRuns;
		this.timeline = timeline;
	}

	public TaskFailureEvidence collect(String taskId) {
		TaskRecord task = tasks.getTask(taskId).orElse(null);
		ExecutionRecordDetail failed = latestFailedExecution(taskId);
		DeliveryPipeline pipeline = taskId == null ? null : delivery.get(taskId);
		PlanRun planRun = null;
		if (task != null && task.getPlanRunId() != null && !task.getPlanRunId().isBlank()) {
			planRun = planRuns.get(task.getPlanRunId());
		}
		List<String> timelineEvidence = failureTimelineEvidence(taskId);
		return new TaskFailureEvidence(task, failed, pipeline, planRun, timelineEvidence);
	}

	/** 只提取与失败相关的关键 Timeline 事件（有限数量、去重、snippet）。 */
	private List<String> failureTimelineEvidence(String taskId) {
		if (taskId == null || timeline == null) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		Set<String> seen = new java.util.HashSet<>();
		try {
			List<TimelineEventDTO> events = timeline.timeline(taskId).events();
			for (int i = events.size() - 1; i >= 0 && result.size() < MAX_TIMELINE_EVIDENCE; i--) {
				TimelineEventDTO event = events.get(i);
				if (!FAILURE_EVENTS.contains(event.eventType())) {
					continue;
				}
				String line = event.eventType() + ": "
					+ snippet(event.message() == null ? "" : event.message());
				if (seen.add(line)) {
					result.add(line);
				}
			}
		}
		catch (RuntimeException ignored) {
			// timeline 不可用时不影响核心诊断
		}
		return result;
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
