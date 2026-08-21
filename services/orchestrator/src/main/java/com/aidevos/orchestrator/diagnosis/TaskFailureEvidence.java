package com.aidevos.orchestrator.diagnosis;

import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.execution.query.ExecutionRecordDetail;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.taskcenter.TaskRecord;

/**
 * V1 Evidence Collector 聚合结果：按 taskId 收集的结构化事实快照。
 * 只含结构化字段 + 关键日志片段（禁止整段 Maven/Spring 日志）。
 */
public record TaskFailureEvidence(
	TaskRecord task,
	/** 最新失败 ExecutionRecord（可空） */
	ExecutionRecordDetail failedExecution,
	/** DeliveryPipeline（可空，含 failureClass/failureReason/currentStage） */
	DeliveryPipeline pipeline,
	/** PlanRun（可空，含 error） */
	PlanRun planRun
) {
}
