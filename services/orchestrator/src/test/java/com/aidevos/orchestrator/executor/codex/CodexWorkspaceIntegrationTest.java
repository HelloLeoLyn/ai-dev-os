package com.aidevos.orchestrator.executor.codex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.agentcoordinator.AgentExecutionPlan;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import com.aidevos.orchestrator.modelrouter.ResolvedModel;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.Workspace;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the real CodexExecutor against a temporary git repository with a fake
 * codex executable, verifying workspace-bound execution, git status/diff
 * capture and the closed-loop persistence (ExecutionRecord + audit).
 */
class CodexWorkspaceIntegrationTest {

	@TempDir
	Path tempDir;

	private Path repo;
	private CommandExecutor commandExecutor;
	private CodexProperties codexProperties;
	private CodexExecutor codexExecutor;
	private WorkspaceService workspaceService;
	private String workspaceId;
	private InMemoryExecutionRecordRepository recordRepository;
	private InMemoryAuditRepository auditRepository;

	@BeforeEach
	void setUp() throws Exception {
		repo = tempDir.resolve("repo");
		Files.createDirectories(repo);
		git(repo, "init", "-b", "main");
		git(repo, "config", "user.email", "test@example.com");
		git(repo, "config", "user.name", "Test");
		write(repo.resolve("a.txt"), "one\n");
		git(repo, "add", "a.txt");
		git(repo, "commit", "-m", "init");

		commandExecutor = new CommandExecutor();
		codexProperties = new CodexProperties();
		codexProperties.setExecutable(executable("codex-success",
			"#!/usr/bin/env bash\nprintf 'change\\n' >> a.txt\necho 'codex executed'\nexit 0\n").toString());
		codexProperties.setApprovalPolicy(CodexApprovalPolicy.NEVER);
		codexProperties.setTimeout(Duration.ofMinutes(1));

		CodingWorkspaceProperties workspaceProperties = new CodingWorkspaceProperties();
		workspaceProperties.setAllowedRoots(List.of(tempDir.toString()));
		WorkspaceResolver workspaceResolver = new WorkspaceResolver(workspaceProperties,
			new GitExecutor(commandExecutor));
		CodexOutputSchemaProvider schemaProvider = mock(CodexOutputSchemaProvider.class);
		when(schemaProvider.path()).thenReturn(tempDir.resolve("schema.json").toString());
		ArtifactContentLimiter limiter = new ArtifactContentLimiter(100_000);
		codexExecutor = new CodexExecutor(commandExecutor, workspaceResolver,
			new GitInspector(new GitExecutor(commandExecutor)),
			new CodexResultMapper(new ObjectMapper()), mock(CodingApprovalService.class), limiter,
			codexProperties, new CodexCommandBuilder(codexProperties, new CoderPromptBuilder(),
				schemaProvider), new UntrackedArtifactCollector(limiter, 100_000));

		workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(),
			new ProcessGitCommandExecutor(commandExecutor));
		workspaceId = workspaceService.createWorkspace("project-x", repo.toString()).getWorkspaceId();
	}

	@Test
	void shouldRunCodexInWorkspaceAndCaptureGitState() throws Exception {
		ExecutionContext context = context(repo.toString());

		ExecutionResult result = codexExecutor.execute(context);

		assertTrue(result.isSuccess());
		assertEquals(0, result.getMetadata().get("exitCode"));
		assertEquals(repo.toString(), result.getMetadata().get("workspace"));
		assertTrue(Files.readString(repo.resolve("a.txt")).contains("change"));
		assertTrue(result.getArtifacts().stream()
			.filter(artifact -> "git-diff-stat".equals(artifact.getType()))
			.anyMatch(artifact -> artifact.getContent() != null
				&& artifact.getContent().contains("a.txt")));
		assertTrue(result.getArtifacts().stream()
			.anyMatch(artifact -> "codex-result".equals(artifact.getType())
				&& artifact.getMetadata().get("branch") != null));

		GitStatus status = workspaceService.checkGitStatus(workspaceId);
		assertEquals(1, status.getModified());
		GitDiff diff = workspaceService.getGitDiff(workspaceId);
		assertTrue(diff.getFilesChanged() >= 1);
		assertTrue(diff.getStat().contains("a.txt"));
	}

	@Test
	void shouldCaptureFailureExitCodeAndStderr() throws Exception {
		codexProperties.setExecutable(executable("codex-fail",
			"#!/usr/bin/env bash\necho 'boom' >&2\nexit 1\n").toString());

		ExecutionResult result = codexExecutor.execute(context(repo.toString()));

		assertFalse(result.isSuccess());
		assertEquals(1, result.getMetadata().get("exitCode"));
		assertTrue(result.getMessage() != null && result.getMessage().contains("boom"));
	}

	@Test
	void shouldPersistGitStateIntoClosedLoopExecutionRecord() {
		TaskRecord task = runClosedLoop();

		assertEquals(TaskStatus.COMPLETED, task.getStatus());
		ExecutionRecord coding = executionRecord("coder");
		assertEquals(repo.toString(), coding.getWorkspace());
		assertEquals(0, coding.getExitCode());
		assertTrue(coding.getGitStatus() != null && coding.getGitStatus().contains("modified=1"));
		assertTrue(coding.getGitDiffStat() != null && coding.getGitDiffStat().contains("a.txt"));
		assertTrue(events().stream().anyMatch(
			event -> event.type() == EventType.CODEX_EXEC_STARTED));
		assertTrue(events().stream().anyMatch(
			event -> event.type() == EventType.CODEX_EXEC_COMPLETED));
	}

	private TaskRecord runClosedLoop() {
		PlannerService plannerService = mock(PlannerService.class);
		PlanApprovalService approvalService = mock(PlanApprovalService.class);
		PlanRunRepository planRunRepository = mock(PlanRunRepository.class);
		ModelRouterService modelRouterService = mock(ModelRouterService.class);

		AgentManager agentManager = new AgentManager();
		agentManager.register(agent("planner", "mock", List.of("planning", "analysis")));
		agentManager.register(agent("coder", "codex", List.of("coding", "git")));
		agentManager.register(agent("tester", "mock", List.of("testing", "browser")));
		agentManager.register(agent("browser-agent", "mock", List.of("browser")));
		ExecutorRegistry registry = new ExecutorRegistry(List.of(new MockAgentExecutor(),
			codexExecutor));
		ExecutorManager executorManager = new ExecutorManager(agentManager, registry);

		auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
		MemoryService memoryService = new MemoryService(memoryRepository);
		recordRepository = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			recordRepository, auditService);

		TaskCenterService taskCenterService = new TaskCenterService(plannerService,
			approvalService, planRunRepository);
		TestAgentService testAgentService = new TestAgentService(new FakeRunner(),
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);
		AgentCoordinatorService coordinator = new AgentCoordinatorService(taskCenterService,
			modelRouterService, plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			workspaceService);
		taskCenterService.setAgentCoordinatorService(coordinator);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);

		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.parse("2026-08-01T00:00:00Z"));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", plan,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);

		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"Implement login", "Append a line to a.txt", "Append a line to a.txt", "hermes",
			"project-x", workspaceId));
		TaskRecord approved = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.APPROVED, approved.getStatus());

		TaskRecord executed = taskCenterService.execute(approved.getTaskId(),
			TaskType.TASK_ANALYSIS);
		List<AgentExecutionPlan> steps = coordinator.getCollaborationPlan(task.getTaskId())
			.orElseThrow();
		assertTrue(steps.stream().allMatch(step -> workspaceId.equals(step.getWorkspaceId())));
		return executed;
	}

	private ExecutionRecord executionRecord(String agentName) {
		return recordRepository.getAll().stream()
			.filter(record -> agentName.equals(record.getAgentName()))
			.findFirst()
			.orElseThrow();
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	private ExecutionContext context(String workspace) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Append a line to a.txt");
		context.setWorkspace(workspace);
		return context;
	}

	private AgentDefinition agent(String name, String executor, List<String> capabilities) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		definition.setVersion("1.0.0");
		definition.setExecutor(executor);
		definition.setCapabilities(capabilities);
		return definition;
	}

	private Path executable(String name, String script) throws Exception {
		Path file = tempDir.resolve(name);
		Files.writeString(file, script, StandardCharsets.UTF_8);
		Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
		return file;
	}

	private void write(Path file, String content) throws Exception {
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private void git(Path directory, String... args) throws Exception {
		String[] command = new String[args.length + 1];
		command[0] = "git";
		System.arraycopy(args, 0, command, 1, args.length);
		Process process = new ProcessBuilder(command)
			.directory(directory.toFile())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = process.waitFor();
		assertEquals(0, exitCode, "git " + String.join(" ", args) + " failed: " + output);
	}

	private static final class FakeRunner implements TestCommandRunner {

		@Override
		public TestCommandResult run(String command, String workdir) {
			return new TestCommandResult(0, "BUILD SUCCESS", "");
		}
	}

	private static final class FakeBrowserExecutor implements BrowserTestExecutor {

		@Override
		public BrowserTestResult execute(String testId, String command) {
			return BrowserTestResult.success("ok", null);
		}
	}
}
