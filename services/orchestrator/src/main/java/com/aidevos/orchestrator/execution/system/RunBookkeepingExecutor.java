package com.aidevos.orchestrator.execution.system;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.model.ExecutionRecord;
import org.springframework.stereotype.Component;

/**
 * Real system bookkeeping: persists a deterministic ExecutionRecord that
 * summarizes the run (task / plan-run / step / attempt correlation) with the
 * "system" executor and the SYSTEM_STEP execution type. No agent or LLM is
 * involved; the record id is returned for step correlation.
 */
@Component
public class RunBookkeepingExecutor implements SystemActionExecutor {

	private final ExecutionRecordRepository recordRepository;

	public RunBookkeepingExecutor(ExecutionRecordRepository recordRepository) {
		this.recordRepository = recordRepository;
	}

	@Override
	public SystemAction action() {
		return SystemAction.RUN_BOOKKEEPING;
	}

	@Override
	public SystemActionResult execute(SystemActionContext context) {
		String id = "system-" + UUID.randomUUID();
		Instant now = Instant.now();
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setExecutionId(id);
		record.setTaskId(context.taskId());
		record.setAgentName("system");
		record.setExecutorName("system");
		record.setOperation("bookkeeping");
		record.setStatus("SUCCESS");
		record.setMessage("Run bookkeeping recorded");
		record.setOutput("bookkeeping:" + context.planRunId() + ":" + context.taskId());
		record.setPlanRunId(context.planRunId());
		record.setStepRunId(context.stepRunId());
		record.setAttemptId(context.attemptId());
		record.setWorkspace(context.workspacePath());
		record.setExecutionType("SYSTEM_STEP");
		record.setStartedAt(now);
		record.setCompletedAt(now);
		recordRepository.save(record);
		return new SystemActionResult(true, "Run bookkeeping recorded", Map.of("recordId", id));
	}
}
