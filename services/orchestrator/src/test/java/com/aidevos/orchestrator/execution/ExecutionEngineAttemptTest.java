package com.aidevos.orchestrator.execution;

import java.time.Duration;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolutionException;
import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionEngineAttemptTest {

	@Test
	void successfulExecutionRecordsStartingRunningAndSucceededAttempt() {
		Harness harness = harness(successfulExecutor());
		TaskDefinition task = task("planner");

		ExecutionResult result = harness.engine().execute(task, "job-1");

		assertTrue(result.isSuccess());
		ExecutionAttempt attempt = harness.attempts().getByJob("job-1").get(0);
		assertEquals(ExecutionAttemptStatus.SUCCEEDED, attempt.getStatus());
		assertNotNull(attempt.getExecutionId());
		assertEquals(1, attempt.getAttemptNo());
		assertNotNull(attempt.getStartedAt());
		assertNotNull(attempt.getCompletedAt());
		assertEquals(attempt.getExecutionId(), harness.records().getAll().get(0).getExecutionId());
		assertEquals(attempt.getId(), harness.records().getAll().get(0).getAttemptId());
	}

	@Test
	void failedResultMarksAttemptFailed() {
		Harness harness = harness(executorReturning(failedResult()));
		ExecutionResult result = harness.engine().execute(task("planner"), "job-1");

		assertEquals(false, result.isSuccess());
		ExecutionAttempt attempt = harness.attempts().getByJob("job-1").get(0);
		assertEquals(ExecutionAttemptStatus.FAILED, attempt.getStatus());
		assertEquals("EXECUTOR_FAILURE", attempt.getFailureCode());
		assertNotNull(attempt.getCompletedAt());
	}

	@Test
	void executorExceptionMarksAttemptFailed() {
		AgentExecutor executor = throwingExecutor();
		Harness harness = harness(executor);

		ExecutionResult result = harness.engine().execute(task("planner"), "job-1");

		assertEquals(false, result.isSuccess());
		ExecutionAttempt attempt = harness.attempts().getByJob("job-1").get(0);
		assertEquals(ExecutionAttemptStatus.FAILED, attempt.getStatus());
		assertEquals("EXECUTOR_FAILURE", attempt.getFailureCode());
	}

	@Test
	void agentResolutionFailureMarksAttemptFailed() {
		AgentResolver resolver = mock(AgentResolver.class);
		when(resolver.resolve(any(TaskDefinition.class)))
			.thenThrow(new AgentResolutionException("no agent for capabilities"));
		ExecutionRecordManager recordManager = records();
		InMemoryExecutionAttemptRepository attempts = new InMemoryExecutionAttemptRepository();
		Harness harness = new Harness(new ExecutionEngine(resolver, recordManager,
			AuditService.noop(), attempts), null, attempts, recordManager);

		ExecutionResult result = harness.engine().execute(task("planner"), "job-1");

		assertEquals(false, result.isSuccess());
		assertEquals("no agent for capabilities", result.getMessage());
		ExecutionAttempt attempt = harness.attempts().getByJob("job-1").get(0);
		assertEquals(ExecutionAttemptStatus.FAILED, attempt.getStatus());
		assertEquals("AGENT_RESOLUTION_FAILURE", attempt.getFailureCode());
	}

	@Test
	void approvalRequiredResultMarksAttemptSucceeded() {
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		ExecutionResult waiting = new ExecutionResult();
		waiting.setApprovalRequired(true);
		waiting.setApprovalId("approval-1");
		when(executor.execute(any(ExecutionContext.class))).thenReturn(waiting);
		Harness harness = harness(executor);

		harness.engine().execute(task("planner"), "job-1");

		ExecutionAttempt attempt = harness.attempts().getByJob("job-1").get(0);
		assertEquals(ExecutionAttemptStatus.SUCCEEDED, attempt.getStatus());
		ExecutionRecord record = harness.records().getAll().get(0);
		assertEquals("WAITING_APPROVAL", record.getStatus());
	}

	@Test
	void attemptNumberIncrementsAcrossExecutionsOfSameJob() {
		Harness harness = harness(successfulExecutor());
		TaskDefinition task = task("planner");

		harness.engine().execute(task, "job-1");
		harness.engine().execute(task, "job-1");

		assertEquals(2, harness.attempts().getByJob("job-1").size());
		assertEquals(1, harness.attempts().getByJob("job-1").get(0).getAttemptNo());
		assertEquals(2, harness.attempts().getByJob("job-1").get(1).getAttemptNo());
	}

	@Test
	void directExecutionRecordsAttemptScopedToExecutionId() {
		Harness harness = harness(successfulExecutor());

		ExecutionResult result = harness.engine().execute(task("planner"));

		assertTrue(result.isSuccess());
		ExecutionRecord record = harness.records().getAll().get(0);
		assertNull(record.getJobId());
		String executionId = record.getExecutionId();
		ExecutionAttempt attempt = harness.attempts().getByJob(executionId).get(0);
		assertEquals(executionId, attempt.getJobId());
		assertEquals(attempt.getId(), record.getAttemptId());
	}

	@Test
	void leaseIsAppliedToAttempt() {
		JobLease lease = new JobLease("worker-1", 7, java.time.Instant.now().plus(Duration.ofMinutes(30)));
		Harness harness = harness(successfulExecutor());

		harness.engine().execute(task("planner"), "job-1", lease);

		ExecutionAttempt attempt = harness.attempts().getByJob("job-1").get(0);
		assertEquals("worker-1", attempt.getLeaseOwner());
		assertEquals(Long.valueOf(7), attempt.getLeaseToken());
		assertNotNull(attempt.getLeaseExpiresAt());
	}

	@Test
	void metadataAttemptIdKeepsPrecedenceOnRecord() {
		Harness harness = harness(successfulExecutor());
		TaskDefinition task = task("planner");
		task.setMetadata(Map.of("attemptId", "step-attempt-1"));

		harness.engine().execute(task, "job-1");

		ExecutionRecord record = harness.records().getAll().get(0);
		assertEquals("step-attempt-1", record.getAttemptId());
		assertEquals(1, harness.attempts().getByJob("job-1").size());
	}

	private Harness harness(AgentExecutor executor) {
		AgentResolver resolver = mock(AgentResolver.class);
		AgentDefinition agent = new AgentDefinition();
		agent.setName("executor");
		agent.setExecutor("mock");
		when(resolver.resolve(any(TaskDefinition.class))).thenReturn(new ResolvedAgent(agent, executor));
		ExecutionRecordManager recordManager = records();
		InMemoryExecutionAttemptRepository attempts = new InMemoryExecutionAttemptRepository();
		ExecutionEngine engine = new ExecutionEngine(resolver, recordManager, AuditService.noop(),
			attempts);
		return new Harness(engine, executor, attempts, recordManager);
	}

	private ExecutionEngine engine(AgentResolver resolver,
			InMemoryExecutionAttemptRepository attempts) {
		return new ExecutionEngine(resolver, new ExecutionRecordManager(), AuditService.noop(),
			attempts);
	}

	private ExecutionRecordManager records() {
		return new ExecutionRecordManager();
	}

	private AgentExecutor successfulExecutor() {
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		when(executor.execute(any(ExecutionContext.class))).thenReturn(result);
		return executor;
	}

	private AgentExecutor executorReturning(ExecutionResult result) {
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		when(executor.execute(any(ExecutionContext.class))).thenReturn(result);
		return executor;
	}

	private AgentExecutor throwingExecutor() {
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		when(executor.execute(any(ExecutionContext.class)))
			.thenThrow(new IllegalStateException("executor down"));
		return executor;
	}

	private ExecutionResult failedResult() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage("agent failed");
		return result;
	}

	private TaskDefinition task(String agentName) {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		task.setDescription("Create an implementation plan");
		task.setAgentName(agentName);
		task.setStatus("pending");
		return task;
	}

	private record Harness(ExecutionEngine engine, AgentExecutor executor,
			InMemoryExecutionAttemptRepository attempts, ExecutionRecordManager records) {
	}
}
