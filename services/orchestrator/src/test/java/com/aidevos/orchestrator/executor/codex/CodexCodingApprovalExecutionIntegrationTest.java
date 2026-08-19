package com.aidevos.orchestrator.executor.codex;

import com.aidevos.orchestrator.modelregistry.ModelTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.approval.ApprovalStore;
import com.aidevos.orchestrator.approval.CodingApprovalProperties;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionAttemptRepository;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionAttemptRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.TestWorkspaceResolvers;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.job.JobWorker;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodexCodingApprovalExecutionIntegrationTest {

	@TempDir Path tempDir;

	@Test
	void approvalResumesSameJobAndRetainsWaitingExecutionHistory() throws Exception {
		Path repo = tempDir.resolve("repo");
		Files.createDirectories(repo);
		git(repo, "init", "-b", "main");
		git(repo, "config", "user.email", "test@example.com");
		git(repo, "config", "user.name", "Test");
		Files.writeString(repo.resolve("source.txt"), "before\n");
		git(repo, "add", "source.txt");
		git(repo, "commit", "-m", "initial");
		Path calls = tempDir.resolve("calls.txt");
		Path executable = tempDir.resolve("codex-test");
		Files.writeString(executable, "#!/usr/bin/env bash\nprintf 'call\\n' >> '" + calls
			+ "'\nprintf 'after\\n' >> source.txt\nprintf '{\"summary\":\"done\"}\\n'\n");
		executable.toFile().setExecutable(true);

		CodingApprovalProperties approvalProperties = new CodingApprovalProperties();
		approvalProperties.setRequiredForWorkspaceWrite(true);
		CodingApprovalService approvals = new CodingApprovalService(new ApprovalStore(),
			approvalProperties);
		CommandExecutor commands = new CommandExecutor();
		CodingWorkspaceProperties workspaceProperties = new CodingWorkspaceProperties();
		workspaceProperties.setAllowedRoots(List.of(tempDir.toString()));
		CodexProperties properties = new CodexProperties();
		properties.setExecutable(executable.toString());
		properties.setTimeout(Duration.ofSeconds(10));
		CodexOutputSchemaProvider schema = mock(CodexOutputSchemaProvider.class);
		Path schemaPath = tempDir.resolve("schema.json");
		Files.writeString(schemaPath, "{}");
		when(schema.path()).thenReturn(schemaPath.toString());
		ArtifactContentLimiter limiter = new ArtifactContentLimiter(100_000);
		CodexExecutor codex = new CodexExecutor(commands,
			TestWorkspaceResolvers.create(workspaceProperties, new GitExecutor(commands)),
			new GitInspector(new GitExecutor(commands)), new CodexResultMapper(new ObjectMapper()),
			approvals, limiter, properties,
			new CodexCommandBuilder(properties, new CoderPromptBuilder(), schema),
			new UntrackedArtifactCollector(limiter, 100_000), null, ModelTestSupport.defaultResolver());

		AgentManager agents = new AgentManager();
		AgentDefinition coder = new AgentDefinition();
		coder.setName("coder"); coder.setExecutor("codex");
		coder.setCapabilities(List.of("coding", "git"));
		agents.register(coder);
		AgentResolver resolver = new AgentResolver(agents, new AgentSelector(agents),
			new ExecutorManager(agents, new ExecutorRegistry(List.of(codex))));
		ExecutionRecordManager records = new ExecutionRecordManager();
		ExecutionAttemptRepository attempts = new InMemoryExecutionAttemptRepository();
		JobStore jobs = new JobStore();
		JobWorker worker = new JobWorker(new ExecutionEngine(resolver, records, attempts), records, 2);
		JobService service = new JobService(jobs, worker);
		worker.start();
		try {
			TaskDefinition task = new TaskDefinition();
			task.setId("step-task"); task.setName("Implement change");
			task.setDescription("Modify source.txt"); task.setAgentName("coder");
			task.setRequiredCapabilities(List.of("coding", "git"));
			task.setMetadata(Map.of("originalTaskId", "task-1", "workspacePath", repo.toString(),
				"executionMode", "READ_WRITE", "planRunId", "run-1", "stepRunId", "step-1",
				"attemptId", "step-attempt-1"));
			String jobId = service.submit(task, "job-1").jobId();
			ExecutionJob waiting = await(service, jobId, JobStatus.WAITING_APPROVAL);
			assertEquals(1, records.getAll().size());
			assertEquals("WAITING_APPROVAL", records.getAll().getFirst().getStatus());
			approvals.approve(waiting.getApprovalId());
			assertTrue(service.resumeAfterApproval(jobId));
			ExecutionJob completed = await(service, jobId, JobStatus.SUCCESS);

			assertEquals(jobId, completed.getId());
			assertEquals(ApprovalStatus.CONSUMED,
				approvals.get(waiting.getApprovalId()).getStatus());
			assertEquals(2, attempts.getByJob(jobId).size());
			assertEquals(2, records.getAll().size());
			assertEquals("WAITING_APPROVAL", records.getAll().getFirst().getStatus());
			assertEquals("SUCCESS", records.getAll().getLast().getStatus());
			assertEquals("codex", records.getAll().getLast().getExecutorName());
			assertTrue(records.getAll().getLast().getArtifacts().stream()
				.anyMatch(a -> "changes.patch".equals(a.getName()) && "git-diff".equals(a.getType())));
			assertEquals(1, Files.readAllLines(calls).size());
		}
		finally { worker.stop(); }
	}

	private ExecutionJob await(JobService service, String id, JobStatus status) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		ExecutionJob current;
		do {
			current = service.get(id);
			if (current.getStatus() == status) return current;
			Thread.sleep(10);
		} while (System.nanoTime() < deadline);
		assertEquals(status, current.getStatus());
		return current;
	}

	private void git(Path directory, String... arguments) throws Exception {
		List<String> command = new java.util.ArrayList<>(); command.add("git"); command.addAll(List.of(arguments));
		Process process = new ProcessBuilder(command).directory(directory.toFile()).start();
		assertEquals(0, process.waitFor());
	}
}
