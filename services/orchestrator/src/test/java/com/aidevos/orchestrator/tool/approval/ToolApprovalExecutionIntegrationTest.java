package com.aidevos.orchestrator.tool.approval;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.ToolExecutor;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.job.JobSubmissionResponse;
import com.aidevos.orchestrator.job.JobWorker;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.tool.DefaultToolArtifactMapper;
import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolContent;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolProvider;
import com.aidevos.orchestrator.tool.ToolRegistry;
import com.aidevos.orchestrator.tool.ToolResult;
import com.aidevos.orchestrator.tool.ToolRouter;
import com.aidevos.orchestrator.tool.policy.AllowRegisteredToolPolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolApprovalExecutionIntegrationTest {

	@Test
	void shouldAutomaticallyAllowReadOnlyTool() {
		AtomicInteger calls = new AtomicInteger();
		Harness harness = harness(ToolAccess.READ_ONLY, invocation -> {
			calls.incrementAndGet();
			return ToolResult.success("READ", List.of(ToolContent.text("read.txt", "READ")));
		});
		try {
			ExecutionResult result = harness.engine.execute(task("read_text_file", Map.of("path", "a")));

			assertTrue(result.isSuccess());
			assertEquals(1, calls.get());
			assertTrue(harness.approvals.getAll().isEmpty());
		}
		finally {
			harness.router.close();
		}
	}

	@Test
	void shouldPauseJobUntilApprovedThenConsumeAndExecute() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		Harness harness = harness(ToolAccess.WORKSPACE_WRITE, invocation -> {
			calls.incrementAndGet();
			return ToolResult.success("WRITTEN", List.of(ToolContent.text("write.txt", "WRITTEN")));
		});
		JobWorker worker = new JobWorker(harness.engine, harness.records, 2);
		JobService jobs = new JobService(new JobStore(), worker);
		worker.start();
		try {
			JobSubmissionResponse submission = jobs.submit(task("write_file",
				Map.of("path", "a.txt", "content", "x")));
			ExecutionJob waiting = await(jobs, submission.jobId(), JobStatus.WAITING_APPROVAL);
			String approvalId = waiting.getApprovalId();

			assertEquals(0, calls.get());
			assertNotNull(approvalId);
			ToolApprovalRequest approval = harness.approvals.approve(approvalId);
			assertEquals(ApprovalStatus.APPROVED, approval.getStatus());
			assertTrue(jobs.resumeAfterApproval(waiting.getId()));
			ExecutionJob completed = await(jobs, waiting.getId(), JobStatus.SUCCESS);

			assertEquals(1, calls.get());
			assertEquals(ApprovalStatus.CONSUMED, approval.getStatus());
			ExecutionRecord record = harness.records.get(completed.getExecutionRecordId());
			assertEquals(approvalId, record.getApprovalId());
			assertEquals("WRITTEN", record.getOutput());
		}
		finally {
			worker.stop();
			harness.router.close();
		}
	}

	@Test
	void shouldRetainApprovalIdWhenApprovedProviderFails() {
		Harness harness = harness(ToolAccess.WORKSPACE_WRITE, invocation -> {
			throw new IllegalStateException("simulated write failure");
		});
		TaskDefinition task = task("write_file", Map.of("path", "a.txt", "content", "x"));
		try {
			ExecutionResult waiting = harness.engine.execute(task);
			String approvalId = waiting.getApprovalId();
			harness.approvals.approve(approvalId);

			ExecutionResult failed = harness.engine.execute(task);
			ExecutionRecord record = harness.records.getAll().getLast();

			assertFalse(failed.isSuccess());
			assertEquals(approvalId, failed.getApprovalId());
			assertEquals("FAILED", record.getStatus());
			assertEquals(approvalId, record.getApprovalId());
		}
		finally {
			harness.router.close();
		}
	}

	private Harness harness(ToolAccess access,
			java.util.function.Function<ToolInvocation, ToolResult> handler) {
		ToolProvider provider = new ToolProvider() {
			@Override public String getId() { return "filesystem"; }
			@Override public List<ToolDefinition> getTools() {
				return List.of(new ToolDefinition("filesystem",
					access == ToolAccess.READ_ONLY ? "read_text_file" : "write_file",
					"Simulated tool", Map.of(), access));
			}
			@Override public ToolResult invoke(ToolInvocation invocation) { return handler.apply(invocation); }
		};
		ToolApprovalService approvals = new ToolApprovalService(new ToolApprovalStore(),
			new ObjectMapper());
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			new AllowRegisteredToolPolicy(), approvals);
		ToolExecutor executor = new ToolExecutor(router,
			new DefaultToolArtifactMapper(new ArtifactContentLimiter(10_000)));
		AgentManager agents = new AgentManager();
		AgentDefinition agent = new AgentDefinition();
		agent.setName("mcp-reader");
		agent.setExecutor("tool");
		agent.setCapabilities(List.of("tool"));
		agents.register(agent);
		AgentResolver resolver = new AgentResolver(agents, new AgentSelector(agents),
			new ExecutorManager(agents, new ExecutorRegistry(List.of(executor))));
		ExecutionRecordManager records = new ExecutionRecordManager();
		return new Harness(router, approvals, records, new ExecutionEngine(resolver, records));
	}

	private TaskDefinition task(String toolName, Map<String, Object> arguments) {
		TaskDefinition task = new TaskDefinition();
		task.setId("tool-approval-task");
		task.setAgentName("mcp-reader");
		task.setParameters(Map.of("tool", Map.of("provider", "filesystem", "name", toolName,
			"arguments", arguments, "invocationId", "approval-invocation-1", "timeout", "PT1S")));
		return task;
	}

	private ExecutionJob await(JobService jobs, String jobId, JobStatus expected) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		ExecutionJob job;
		do {
			job = jobs.get(jobId);
			if (job.getStatus() == expected) {
				return job;
			}
			Thread.sleep(10);
		} while (System.nanoTime() < deadline);
		assertEquals(expected, job.getStatus());
		return job;
	}

	private record Harness(ToolRouter router, ToolApprovalService approvals,
		ExecutionRecordManager records, ExecutionEngine engine) {
	}
}
