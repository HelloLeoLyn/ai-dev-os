package com.aidevos.orchestrator.repair;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.executor.codex.CodexCommandBuilder;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.executor.codex.CodexOutputSchemaProvider;
import com.aidevos.orchestrator.executor.codex.CodexProperties;
import com.aidevos.orchestrator.executor.codex.CodexResultMapper;
import com.aidevos.orchestrator.executor.codex.CoderPromptBuilder;
import com.aidevos.orchestrator.executor.codex.CodexApprovalPolicy;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
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
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.timeline.TimelineEventDTO;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.WorkspaceService;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import com.aidevos.orchestrator.testfixture.ExecutionWorkspaceTestFixture;
import com.aidevos.orchestrator.testfixture.ExecutionAwareWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end repair loop: closed-loop task fails its test, then the repair
 * coordinator analyzes with Hermes, applies a codex fix in the workspace and
 * re-verifies with a now-passing test run. Asserts Memory (resolved bug),
 * Audit (REPAIR_*) and Timeline visibility.
 */
class RepairLoopIntegrationTest {

	@TempDir
	Path tempDir;

	private Path repo;
	private RetryRunner runner;
	private PlannerService plannerService;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private ModelRouterService modelRouterService;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private InMemoryAuditRepository auditRepository;
	private InMemoryMemoryRepository memoryRepository;
	private MemoryService memoryService;
	private InMemoryExecutionRecordRepository recordRepository;
	private RepairCoordinator repairCoordinator;
	private String workspaceId;
	private ExecutionWorkspaceService executionWorkspaceService;

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

		CommandExecutor commandExecutor = new CommandExecutor();
		WorkspaceService workspaceService = new WorkspaceService(new ExecutionAwareWorkspaceRepository(tempDir.resolve("execution-workspaces")),
			new ProcessGitCommandExecutor(commandExecutor));
		executionWorkspaceService = ExecutionWorkspaceTestFixture.service(workspaceService, tempDir);
		workspaceId = workspaceService.createWorkspace("project-x", repo.toString()).getWorkspaceId();

		CodexProperties codexProperties = new CodexProperties();
		codexProperties.setExecutable(executable("codex",
			"#!/usr/bin/env bash\nprintf 'fix\\n' >> a.txt\necho 'fixed'\nexit 0\n").toString());
		codexProperties.setApprovalPolicy(CodexApprovalPolicy.NEVER);
		codexProperties.setTimeout(Duration.ofMinutes(1));
		CodingWorkspaceProperties workspaceProperties = new CodingWorkspaceProperties();
		workspaceProperties.setAllowedRoots(List.of(tempDir.toString()));
		CodexOutputSchemaProvider schemaProvider = mock(CodexOutputSchemaProvider.class);
		when(schemaProvider.path()).thenReturn(tempDir.resolve("schema.json").toString());
		ArtifactContentLimiter limiter = new ArtifactContentLimiter(100_000);
		CodexExecutor codexExecutor = new CodexExecutor(commandExecutor,
			com.aidevos.orchestrator.execution.workspace.TestWorkspaceResolvers.create(workspaceProperties, new GitExecutor(commandExecutor)),
			new GitInspector(new GitExecutor(commandExecutor)),
			new CodexResultMapper(new ObjectMapper()), mock(CodingApprovalService.class), limiter,
			codexProperties, new CodexCommandBuilder(codexProperties, new CoderPromptBuilder(),
				schemaProvider), new UntrackedArtifactCollector(limiter, 100_000));

		plannerService = mock(PlannerService.class);
		approvalService = mock(PlanApprovalService.class);
		planRunRepository = mock(PlanRunRepository.class);
		modelRouterService = mock(ModelRouterService.class);

		AgentManager agentManager = new AgentManager();
		agentManager.register(agent("planner", "mock", List.of("planning", "analysis")));
		agentManager.register(agent("coder", "codex", List.of("coding", "git")));
		agentManager.register(agent("tester", "mock", List.of("testing", "browser")));
		agentManager.register(agent("browser-agent", "mock", List.of("browser")));
		ExecutorManager executorManager = new ExecutorManager(agentManager,
			new ExecutorRegistry(List.of(new MockAgentExecutor(), codexExecutor)));

		auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		memoryRepository = new InMemoryMemoryRepository();
		memoryService = new MemoryService(memoryRepository);
		recordRepository = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			recordRepository, auditService);

		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		runner = new RetryRunner();
		TestAgentService testAgentService = new TestAgentService(runner,
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			workspaceService, null, null, null, null, executionWorkspaceService);
		taskCenterService.setAgentCoordinatorService(coordinator);
		ChangeService changeService = new ChangeService(new InMemoryChangeRepository(),
			workspaceService, auditService);
		repairCoordinator = new RepairCoordinator(taskCenterService, testAgentService,
			plannerService, codexExecutor, workspaceService, memoryService, auditService,
			changeService);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
	}

	@Test
	void shouldRepairFailedTaskAndPersistSolution() throws Exception {
		TaskRecord task = createApprovedTask();
		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);
		assertEquals(TaskStatus.FAILED, executed.getStatus());

		RepairTask repair = repairCoordinator.start(task.getTaskId());

		assertEquals(RepairStatus.SUCCESS, repair.getStatus());
		assertTrue(repair.getLastResult().contains("attempt"));
		Path executionPath = Path.of(executionWorkspaceService.findByTaskId(task.getTaskId())
			.getExecutionWorkspace());
		assertTrue(Files.readString(executionPath.resolve("a.txt")).contains("fix"));

		// Memory: BUG_RECORD resolved=true + AGENT_EXPERIENCE.
		MemoryRecord bug = memoryRepository.list("project-x", MemoryType.BUG_RECORD).stream()
			.filter(record -> ("bug:repair:" + task.getTaskId()).equals(record.getKey()))
			.findFirst().orElseThrow();
		assertEquals(Boolean.TRUE, bug.getResolved());
		assertTrue(bug.getSolution() != null && bug.getSolution().contains("attempt"));
		assertTrue(memoryRepository.list("project-x", MemoryType.AGENT_EXPERIENCE).stream()
			.anyMatch(record -> ("experience:repair:" + task.getTaskId()).equals(record.getKey())));

		// Audit: repair events on the task.
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_SUCCESS
			&& task.getTaskId().equals(event.taskId())));

		// Timeline: Task -> Execution -> Test Failed -> Repair -> Retry Test -> Result.
		TimelineService timelineService = new TimelineService(auditRepository, planRunRepository,
			new JobStore(), recordRepository, new TaskManager(), taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		assertEquals("TASK", timeline.scopeType());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		assertTrue(eventTypes.contains("TEST_FAILED"), "missing test failure: " + eventTypes);
		assertTrue(eventTypes.contains("REPAIR_STARTED"), "missing repair start: " + eventTypes);
		assertTrue(eventTypes.contains("REPAIR_VERIFYING"), "missing verify: " + eventTypes);
		assertTrue(eventTypes.contains("TEST_SUCCEEDED"), "missing retry test: " + eventTypes);
		assertTrue(eventTypes.contains("REPAIR_SUCCESS"), "missing repair success: " + eventTypes);
	}

	private TaskRecord createApprovedTask() {
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
		return approved;
	}

	private List<com.aidevos.orchestrator.audit.EventRecord> events() {
		return auditRepository.query(com.aidevos.orchestrator.audit.EventQuery.all());
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

	/** Fails the first test run, passes every later run. */
	private static final class RetryRunner implements TestCommandRunner {

		private int calls;

		@Override
		public TestCommandResult run(String command, String workdir) {
			calls++;
			if (calls == 1) {
				return new TestCommandResult(1, "FAIL", "BUILD FAILURE");
			}
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
