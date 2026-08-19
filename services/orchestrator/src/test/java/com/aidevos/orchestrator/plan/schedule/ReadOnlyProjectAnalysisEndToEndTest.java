package com.aidevos.orchestrator.plan.schedule;

import com.aidevos.orchestrator.modelregistry.ModelTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionAttemptRepository;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.executor.codex.CodexApprovalPolicy;
import com.aidevos.orchestrator.executor.codex.CodexCommandBuilder;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.executor.codex.CodexOutputSchemaProvider;
import com.aidevos.orchestrator.executor.codex.CodexProperties;
import com.aidevos.orchestrator.executor.codex.CodexResultMapper;
import com.aidevos.orchestrator.executor.codex.CoderPromptBuilder;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.job.JobWorker;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.plan.PlanSnapshotFactory;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.approval.PlanApprovalStore;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.run.StepRunStatus;
import com.aidevos.orchestrator.planner.HermesPlanner;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.replan.FailureClassifier;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import com.aidevos.orchestrator.planner.replan.ReplanRequestStore;
import com.aidevos.orchestrator.planner.replan.ReplanValidator;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.InMemoryTaskRepository;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadOnlyProjectAnalysisEndToEndTest {

	@TempDir
	Path tempDir;

	@Test
	void readsFixtureThroughRealCodexExecutorAndCompletesWithoutWorkspaceChanges() throws Exception {
		Path workspace = fixture();
		String beforeHead = git(workspace, "rev-parse", "HEAD");
		String beforeStatus = git(workspace, "status", "--porcelain=v1");
		String beforeDiff = git(workspace, "diff", "--binary");

		CommandExecutor commands = new CommandExecutor();
		ArtifactContentLimiter limiter = new ArtifactContentLimiter(100_000);
		CodexProperties properties = new CodexProperties();
		properties.setExecutable(analysisExecutable().toString());
		properties.setApprovalPolicy(CodexApprovalPolicy.NEVER);
		properties.setTimeout(Duration.ofMinutes(1));
		CodingWorkspaceProperties workspaceProperties = new CodingWorkspaceProperties();
		workspaceProperties.setAllowedRoots(List.of(tempDir.toString()));
		CodexOutputSchemaProvider schema = mock(CodexOutputSchemaProvider.class);
		when(schema.path()).thenReturn(tempDir.resolve("schema.json").toString());
		when(schema.path(true)).thenReturn(tempDir.resolve("analysis-schema.json").toString());
		CodexExecutor codex = new CodexExecutor(commands,
			com.aidevos.orchestrator.execution.workspace.TestWorkspaceResolvers.create(workspaceProperties, new GitExecutor(commands)),
			new GitInspector(new GitExecutor(commands)), new CodexResultMapper(new ObjectMapper()),
			mock(CodingApprovalService.class), limiter, properties,
			new CodexCommandBuilder(properties, new CoderPromptBuilder(), schema),
			new UntrackedArtifactCollector(limiter, 100_000), null, ModelTestSupport.defaultResolver());

		AgentManager agents = new AgentManager();
		agents.register(agent("planner", "mock", null, List.of("planning", "analysis")));
		agents.register(agent("analyst", "codex", "read-only",
			List.of("analysis", "read-only")));
		ExecutorRegistry executors = new ExecutorRegistry(List.of(new MockAgentExecutor(), codex));
		InMemoryAuditRepository eventStore = new InMemoryAuditRepository();
		AuditService audit = new AuditService(eventStore);
		InMemoryExecutionRecordRepository recordStore = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager records = new ExecutionRecordManager(recordStore, audit);
		ExecutionEngine engine = new ExecutionEngine(new AgentResolver(agents,
			new AgentSelector(agents), new ExecutorManager(agents, executors), audit), records, audit,
			new InMemoryExecutionAttemptRepository());
		JobStore jobs = new JobStore();
		JobWorker worker = new JobWorker(engine, records, jobs, audit, 8);
		worker.start();
		PlanScheduler scheduler = null;
		try {
			JobService jobService = new JobService(jobs, worker, audit);
			PlanValidator validator = new PlanValidator();
			PlannerService planners = new PlannerService(List.of(new HermesPlanner()), validator,
				new ReplanValidator(validator), audit,
				new PlanSnapshotFactory(agents, new ToolRegistry(List.of()), executors), "v1");
			PlanApprovalService approvals = new PlanApprovalService(new PlanApprovalStore(), validator,
				new ObjectMapper(), audit);
			InMemoryPlanRunRepository runs = new InMemoryPlanRunRepository();
			scheduler = new PlanScheduler(jobService, new StepTaskFactory(), approvals,
				new ReplanRequestService(new ReplanRequestStore(), new FailureClassifier()),
				runs, java.time.Clock.systemUTC(), audit);
			scheduler.startMonitor();
			TaskCenterService tasks = new TaskCenterService(planners, approvals, runs, null, audit,
				new InMemoryTaskRepository(), scheduler);

			TaskRecord task = tasks.createTask(new CreateTaskRequest("Analyze fixture",
				"Read the fixture", "Analyze modules and technology evidence", "hermes",
				"project-fixture", "workspace-fixture", ExecutionMode.READ_ONLY),
				workspace.toString());
			assertEquals(TaskStatus.PLANNING, task.getStatus());
			assertEquals("analyst", approvals.get(task.getApprovalId()).getPlan().steps().getFirst()
				.assignment().agentName());
			assertEquals("codex-result", approvals.get(task.getApprovalId()).getPlan().steps()
				.getFirst().expectedArtifacts().getFirst().type());

			tasks.approve(task.getTaskId(), "reviewer");
			TaskRecord completed = awaitTerminal(tasks, task.getTaskId());
			assertEquals(TaskStatus.SUCCESS, completed.getStatus());
			PlanRun run = runs.get(completed.getPlanRunId());
			assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
			assertEquals(StepRunStatus.SUCCESS, run.getSteps().getFirst().getStatus());

			var record = records.getAll().stream()
				.filter(candidate -> "analyst".equals(candidate.getAgentName()))
				.findFirst().orElseThrow();
			List<ExecutionArtifact> artifacts = record.getArtifacts();
			ExecutionArtifact result = artifacts.stream()
				.filter(artifact -> "codex-result".equals(artifact.getType()))
				.findFirst().orElseThrow();
			assertTrue(artifacts.size() > 0);
			assertTrue(artifacts.stream().anyMatch(artifact -> "analysis-result".equals(artifact.getType())
				&& "application/json".equals(artifact.getMediaType())));
			assertTrue(result.getContent().contains("NebulaBackend"));
			assertTrue(result.getContent().contains("Spring Boot 3.5"));
			assertTrue(result.getContent().contains("OrionFrontend"));
			assertTrue(result.getContent().contains("Vue 3.6"));
			assertTrue(result.getContent().contains("AtlasDocs"));
			assertEquals("read-only", result.getMetadata().get("sandbox"));

			Set<EventType> eventTypes = eventStore.query(EventQuery.all()).stream()
				.filter(event -> task.getTaskId().equals(event.taskId()))
				.map(event -> event.type()).collect(java.util.stream.Collectors.toSet());
			assertTrue(eventTypes.containsAll(Set.of(EventType.PLAN_CREATED,
				EventType.PLAN_APPROVAL_REQUESTED, EventType.PLAN_APPROVAL_APPROVED,
				EventType.PLAN_RUN_CREATED, EventType.STEP_SUCCEEDED,
				EventType.PLAN_RUN_SUCCEEDED)));
			assertEquals(beforeHead, git(workspace, "rev-parse", "HEAD"));
			assertEquals(beforeStatus, git(workspace, "status", "--porcelain=v1"));
			assertEquals(beforeDiff, git(workspace, "diff", "--binary"));
			assertTrue(new InMemoryChangeRepository().list().isEmpty());
			assertFalse(Files.exists(workspace.resolve("analysis-output.txt")));
		}
		finally {
			if (scheduler != null) scheduler.stopMonitor();
			worker.stop();
		}
	}

	private TaskRecord awaitTerminal(TaskCenterService tasks, String taskId) throws Exception {
		for (int i = 0; i < 200; i++) {
			TaskRecord task = tasks.getTask(taskId).orElseThrow();
			if (task.getStatus() == TaskStatus.SUCCESS || task.getStatus() == TaskStatus.FAILED) {
				return task;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Task did not complete");
	}

	private Path fixture() throws Exception {
		Path workspace = tempDir.resolve("fixture");
		Files.createDirectories(workspace.resolve("backend"));
		Files.createDirectories(workspace.resolve("frontend"));
		Files.createDirectories(workspace.resolve("docs"));
		Files.writeString(workspace.resolve("README.md"), "Project NebulaPlatform\n");
		Files.writeString(workspace.resolve("backend/pom.xml"),
			"<project><name>NebulaBackend</name><properties>Spring Boot 3.5</properties></project>\n");
		Files.writeString(workspace.resolve("frontend/package.json"),
			"{\"name\":\"OrionFrontend\",\"framework\":\"Vue 3.6\"}\n");
		Files.writeString(workspace.resolve("docs/README.md"), "AtlasDocs architecture guide\n");
		git(workspace, "init", "-b", "main");
		git(workspace, "config", "user.email", "test@example.com");
		git(workspace, "config", "user.name", "Test");
		git(workspace, "add", ".");
		git(workspace, "commit", "-m", "fixture");
		return workspace;
	}

	private Path analysisExecutable() throws Exception {
		Path executable = tempDir.resolve("codex-analysis");
		Files.writeString(executable, "#!/usr/bin/env bash\n"
			+ "set -eu\n"
			+ "evidence=$(printf '%s | %s | %s | %s' \"$(cat README.md)\" \"$(cat backend/pom.xml)\" \"$(cat frontend/package.json)\" \"$(cat docs/README.md)\")\n"
			+ "evidence=${evidence//\\\"/}\n"
			+ "printf '{\"type\":\"thread.started\",\"thread_id\":\"fixture-thread\"}\\n'\n"
			+ "printf '%s\\n' '{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"{\\\"schemaVersion\\\":\\\"1.0\\\",\\\"summary\\\":\\\"NebulaBackend Spring Boot 3.5 OrionFrontend Vue 3.6 AtlasDocs\\\",\\\"findings\\\":[],\\\"recommendations\\\":[]}\"}}'\n");
		executable.toFile().setExecutable(true);
		return executable;
	}

	private AgentDefinition agent(String name, String executor, String permission,
			List<String> capabilities) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name);
		agent.setExecutor(executor);
		agent.setPermissionLevel(permission);
		agent.setCapabilities(capabilities);
		agent.setEnabled(true);
		return agent;
	}

	private String git(Path workspace, String... args) throws Exception {
		List<String> command = new java.util.ArrayList<>();
		command.add("git");
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).directory(workspace.toFile())
			.redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes()).trim();
		assertEquals(0, process.waitFor(), output);
		return output;
	}
}
