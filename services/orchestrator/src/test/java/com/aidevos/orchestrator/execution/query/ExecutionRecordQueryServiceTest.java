package com.aidevos.orchestrator.execution.query;

import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionReport;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.model.ExecutionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRecordQueryServiceTest {

	private ExecutionRecordManager manager;
	private ExecutionRecordQueryService service;

	@BeforeEach
	void setUp() {
		manager = new ExecutionRecordManager();
		service = new ExecutionRecordQueryService(manager);
	}

	@Test
	void shouldReturnEmptyList() {
		assertTrue(service.getAll(null, null).isEmpty());
	}

	@Test
	void shouldReturnAllSummariesInInsertionOrder() {
		manager.save(record("record-1", "task-1", "SUCCESS"));
		manager.save(record("record-2", "task-2", "FAILED"));

		List<ExecutionRecordSummary> summaries = service.getAll(null, null);

		assertEquals(2, summaries.size());
		assertEquals("record-1", summaries.getFirst().id());
		assertEquals("record-2", summaries.getLast().id());
		assertEquals("coder", summaries.getFirst().agentName());
	}

	@Test
	void shouldFilterByStatusIgnoringCase() {
		manager.save(record("record-1", "task-1", "SUCCESS"));
		manager.save(record("record-2", "task-2", "FAILED"));

		List<ExecutionRecordSummary> summaries = service.getAll("success", null);

		assertEquals(1, summaries.size());
		assertEquals("record-1", summaries.getFirst().id());
	}

	@Test
	void shouldFilterByTaskId() {
		manager.save(record("record-1", "task-1", "SUCCESS"));
		manager.save(record("record-2", "task-2", "SUCCESS"));

		List<ExecutionRecordSummary> summaries = service.getAll(null, "task-2");

		assertEquals(1, summaries.size());
		assertEquals("record-2", summaries.getFirst().id());
	}

	@Test
	void shouldReturnFullDetail() {
		ExecutionRecord record = record("record-1", "task-1", "SUCCESS");
		manager.save(record);

		ExecutionRecordDetail detail = service.get("record-1").orElseThrow();

		assertEquals("execution output", detail.output());
		assertEquals(record.getReport(), detail.report());
		assertEquals("git diff", detail.report().getAfterGitDiff());
		assertEquals(1, detail.artifacts().size());
		assertEquals("git-diff", detail.artifacts().getFirst().getType());
		assertEquals("/workspace", detail.workspace());
		assertEquals("workspace-write", detail.sandbox());
		assertEquals("codex", detail.executorName());
		assertEquals("run-1", detail.planRunId());
		assertEquals("step-1", detail.stepRunId());
		assertEquals("attempt-1", detail.attemptId());
		assertFalse(service.get("unknown").isPresent());
	}

	private ExecutionRecord record(String id, String taskId, String status) {
		ExecutionReport report = new ExecutionReport();
		report.setTaskId(taskId);
		report.setAgentName("coder");
		report.setSuccess("SUCCESS".equals(status));
		report.setBeforeGitStatus("git status");
		report.setAfterGitDiff("git diff");
		report.setOutput("execution output");
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setTaskId(taskId);
		record.setAgentName("coder");
		record.setExecutorName("codex");
		record.setStatus(status);
		record.setMessage("message");
		record.setOutput("execution output");
		record.setReport(report);
		ExecutionArtifact artifact = new ExecutionArtifact();
		artifact.setType("git-diff");
		record.setArtifacts(List.of(artifact));
		record.setWorkspace("/workspace");
		record.setSandbox("workspace-write");
		record.setPlanRunId("run-1");
		record.setStepRunId("step-1");
		record.setAttemptId("attempt-1");
		return record;
	}
}
