package com.aidevos.orchestrator.tool;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
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
import com.aidevos.orchestrator.tool.policy.AllowRegisteredToolPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolJobExecutionIntegrationTest {

	@Test
	void shouldExecuteExplicitToolThroughJobWorker() throws Exception {
		ToolProvider provider = new FakeToolProvider("filesystem", "read_text_file",
			invocation -> ToolResult.success("FILE", List.of(ToolContent.text("file.txt", "FILE"))));
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(provider)),
			new AllowRegisteredToolPolicy());
		ToolExecutor toolExecutor = new ToolExecutor(router,
			new DefaultToolArtifactMapper(new ArtifactContentLimiter(10_000)));
		AgentManager agents = new AgentManager();
		AgentDefinition agent = new AgentDefinition();
		agent.setName("mcp-reader");
		agent.setExecutor("tool");
		agent.setCapabilities(List.of("tool", "read-only"));
		agents.register(agent);
		AgentResolver resolver = new AgentResolver(agents, new AgentSelector(agents),
			new ExecutorManager(agents, new ExecutorRegistry(List.of(toolExecutor))));
		ExecutionRecordManager records = new ExecutionRecordManager();
		ExecutionEngine engine = new ExecutionEngine(resolver, records);
		JobWorker worker = new JobWorker(engine, records, 2);
		JobStore store = new JobStore();
		JobService jobs = new JobService(store, worker);
		worker.start();
		try {
			TaskDefinition task = new TaskDefinition();
			task.setId("filesystem-job-task");
			task.setAgentName("mcp-reader");
			task.setParameters(Map.of("tool", Map.of("provider", "filesystem",
				"name", "read_text_file", "arguments", Map.of("path", "README.md"))));

			JobSubmissionResponse submission = jobs.submit(task);
			ExecutionJob job = await(jobs, submission.jobId());

			assertEquals(JobStatus.SUCCESS, job.getStatus());
			assertNotNull(job.getExecutionRecordId());
			ExecutionRecord record = records.get(job.getExecutionRecordId());
			assertEquals(job.getId(), record.getJobId());
			assertEquals("FILE", record.getOutput());
			assertEquals(1, record.getArtifacts().size());
		}
		finally {
			worker.stop();
			router.close();
		}
	}

	private ExecutionJob await(JobService jobs, String jobId) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
		ExecutionJob job;
		do {
			job = jobs.get(jobId);
			if (job.getStatus() == JobStatus.SUCCESS || job.getStatus() == JobStatus.FAILED) {
				return job;
			}
			Thread.sleep(10);
		} while (System.nanoTime() < deadline);
		return job;
	}
}
