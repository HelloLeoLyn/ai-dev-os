package com.aidevos.orchestrator.ci;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.change.ChangeSet;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.commit.InMemoryCommitRepository;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
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
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
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
import com.aidevos.orchestrator.pr.InMemoryPullRequestRepository;
import com.aidevos.orchestrator.pr.MockPullRequestProvider;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.pr.PullRequestStatus;
import com.aidevos.orchestrator.pr.provider.GitProviderProperties;
import com.aidevos.orchestrator.remote.InMemoryRemoteRepository;
import com.aidevos.orchestrator.remote.RemoteBranchRecord;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemoteStatus;
import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.CiFailureAnalyzer;
import com.aidevos.orchestrator.repair.RepairCoordinator;
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
 * End-to-end CI integration: a closed-loop commit is pushed, a PR is opened
 * and a CI check attaches a CiRunRecord that moves RUNNING -> SUCCESS (or
 * FAILED which builds a CI_FAILURE FailureContext and starts the repair
 * loop). Asserts the CI_* audit events and the timeline show PR_CREATED ->
 * CI_STARTED -> CI_SUCCESS / CI_FAILED -> REPAIR_STARTED -> REPAIR_SUCCESS.
 */
class CiLifecycleIntegrationTest {

	@TempDir
	Path tempDir;

	private Path repo;
	private Path bare;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private ChangeService changeService;
	private CommitService commitService;
	private RemoteGitService remoteGitService;
	private PullRequestService pullRequestService;
	private CiService ciService;
	private MockCiProvider mockCiProvider;
	private RepairCoordinator repairCoordinator;
	private InMemoryAuditRepository auditRepository;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private String workspaceId;

	@BeforeEach
	void setUp() throws Exception {
		repo = tempDir.resolve("repo");
		Files.createDirectories(repo);
		bare = tempDir.resolve("bare.git");
		git(tempDir, "init", "--bare", bare.getFileName().toString());
		git(repo, "init", "-b", "main");
		git(repo, "config", "user.email", "test@example.com");
		git(repo, "config", "user.name", "Test");
		git(repo, "remote", "add", "origin", bare.toString());
		write(repo.resolve("a.txt"), "one\n");
		git(repo, "add", "a.txt");
		git(repo, "commit", "-m", "init");

		CommandExecutor commandExecutor = new CommandExecutor();
		WorkspaceService workspaceService = new WorkspaceService(new ExecutionAwareWorkspaceRepository(tempDir.resolve("execution-workspaces")),
			new ProcessGitCommandExecutor(commandExecutor));
		ExecutionWorkspaceService executionWorkspaceService = ExecutionWorkspaceTestFixture.service(workspaceService, tempDir);
		workspaceId = workspaceService.createWorkspace("project-x", repo.toString()).getWorkspaceId();

		CodexProperties codexProperties = new CodexProperties();
		codexProperties.setExecutable(executable("codex",
			"#!/usr/bin/env bash\nprintf 'change\\n' >> a.txt\necho 'codex executed'\nexit 0\n").toString());
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

		PlannerService plannerService = mock(PlannerService.class);
		approvalService = mock(PlanApprovalService.class);
		planRunRepository = mock(PlanRunRepository.class);
		ModelRouterService modelRouterService = mock(ModelRouterService.class);

		AgentManager agentManager = new AgentManager();
		agentManager.register(agent("planner", "mock", List.of("planning", "analysis")));
		agentManager.register(agent("coder", "codex", List.of("coding", "git")));
		agentManager.register(agent("tester", "mock", List.of("testing", "browser")));
		agentManager.register(agent("browser-agent", "mock", List.of("browser")));
		ExecutorManager executorManager = new ExecutorManager(agentManager,
			new ExecutorRegistry(List.of(new MockAgentExecutor(), codexExecutor)));

		auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		MemoryService memoryService = new MemoryService(new InMemoryMemoryRepository());
		InMemoryExecutionRecordRepository recordRepository = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			recordRepository, auditService);

		changeService = new ChangeService(new InMemoryChangeRepository(), workspaceService,
			auditService);
		commitService = new CommitService(new InMemoryCommitRepository(), changeService,
			workspaceService, new ProcessGitCommandExecutor(commandExecutor), auditService);
		remoteGitService = new RemoteGitService(new InMemoryRemoteRepository(), commitService,
			workspaceService, new ProcessGitCommandExecutor(commandExecutor), auditService);
		pullRequestService = new PullRequestService(new InMemoryPullRequestRepository(),
			commitService, remoteGitService, new MockPullRequestProvider(),
			new GitProviderProperties(), auditService);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		TestAgentService testAgentService = new TestAgentService(new FakeRunner(),
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			workspaceService, changeService, null, null, null, executionWorkspaceService);
		taskCenterService.setAgentCoordinatorService(coordinator);

		mockCiProvider = new MockCiProvider();
		repairCoordinator = new RepairCoordinator(taskCenterService, testAgentService,
			plannerService, codexExecutor, workspaceService, memoryService, auditService,
			changeService);
		ciService = new CiService(new InMemoryCiRepository(), mockCiProvider,
			new CiProviderProperties(), pullRequestService, commitService, repairCoordinator,
			new CiFailureAnalyzer(workspaceService, changeService), auditService);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
	}

	@Test
	void shouldCheckCiAndReachSuccessAfterPullRequestCreated() throws Exception {
		TaskRecord task = createApprovedTask();
		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);
		assertEquals(TaskStatus.COMPLETED, executed.getStatus());
		CommitRecord commit = commitAndPush(task.getTaskId());
		PullRequestRecord pullRequest = pullRequestService.createPullRequest(
			commit.getCommitId(), null);
		assertEquals(PullRequestStatus.OPEN, pullRequest.getStatus());

		// First check creates the run (RUNNING), second check observes SUCCESS.
		CiRunRecord first = ciService.check(pullRequest.getPullRequestId());
		assertEquals(CiStatus.RUNNING, first.getStatus());
		assertEquals(commit.getGitHash(), first.getCommitHash());
		mockCiProvider.setStatus("pipeline-" + pullRequest.getPullRequestId(),
			CiStatus.SUCCESS);
		CiRunRecord checked = ciService.check(pullRequest.getPullRequestId());

		assertEquals(CiStatus.SUCCESS, checked.getStatus());
		assertEquals(pullRequest.getPullRequestId(), checked.getPullRequestId());
		assertEquals(1, ciService.getByTask(task.getTaskId()).size());
		assertEquals(checked, ciService.get(checked.getCiRunId()).orElseThrow());

		// Audit + Timeline: PR then CI_STARTED then CI_SUCCESS on the task.
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_STARTED
			&& task.getTaskId().equals(event.taskId())
			&& checked.getCiRunId().equals(event.aggregateId())
			&& "mock".equals(event.metadata().get("provider"))));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_SUCCESS
			&& task.getTaskId().equals(event.taskId())));

		TimelineService timelineService = new TimelineService(auditRepository, planRunRepository,
			new JobStore(), new InMemoryExecutionRecordRepository(), new TaskManager(),
			taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		assertEquals("TASK", timeline.scopeType());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		assertTrue(eventTypes.contains("PR_OPENED"), "missing pr: " + eventTypes);
		assertTrue(eventTypes.contains("CI_STARTED"), "missing ci start: " + eventTypes);
		assertTrue(eventTypes.contains("CI_SUCCESS"), "missing ci success: " + eventTypes);
	}

	@Test
	void shouldMarkFailedAndRepairFromCiFailure() throws Exception {
		TaskRecord task = createApprovedTask();
		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);
		assertEquals(TaskStatus.COMPLETED, executed.getStatus());
		CommitRecord commit = commitAndPush(task.getTaskId());
		PullRequestRecord pullRequest = pullRequestService.createPullRequest(
			commit.getCommitId(), null);

		CiRunRecord first = ciService.check(pullRequest.getPullRequestId());
		assertEquals(CiStatus.RUNNING, first.getStatus());
		mockCiProvider.setStatus("pipeline-" + pullRequest.getPullRequestId(),
			CiStatus.FAILED);
		CiRunRecord checked = ciService.check(pullRequest.getPullRequestId());

		assertEquals(CiStatus.FAILED, checked.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_FAILED
			&& task.getTaskId().equals(event.taskId())));

		// FailureContext: CI_FAILURE source with the run details attached.
		FailureContext context = repairCoordinator.getFailureContext(task.getTaskId())
			.orElseThrow();
		assertEquals(task.getTaskId(), context.taskId());
		assertEquals(workspaceId, context.workspaceId());
		assertEquals("https://mock.dev/ci/pipeline-" + pullRequest.getPullRequestId(),
			context.testReport());
		assertEquals("CI_FAILURE", context.sourceType());
		assertEquals(checked.getCiRunId(), context.sourceId());
		assertEquals(commit.getGitHash(), context.commitHash());
		assertEquals("ai-dev-os/task/" + task.getTaskId(), context.branch());
		assertTrue(context.changedFiles() >= 1);

		// Repair loop: CI_FAILED -> CI_REPAIR_STARTED -> ... -> REPAIR_SUCCESS.
		com.aidevos.orchestrator.repair.RepairTask repair = repairCoordinator
			.getByCiRun(checked.getCiRunId()).orElseThrow();
		assertEquals(com.aidevos.orchestrator.repair.RepairStatus.SUCCESS, repair.getStatus());
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_REPAIR_STARTED
			&& task.getTaskId().equals(event.taskId())
			&& repair.getRepairId().equals(event.aggregateId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.CI_REPAIR_SUCCESS
			&& task.getTaskId().equals(event.taskId())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.REPAIR_SUCCESS
			&& task.getTaskId().equals(event.taskId())));

		// Successful repair snapshots a ChangeSet for manual review (CREATED).
		List<com.aidevos.orchestrator.change.ChangeSet> changes = changeService
			.getChangesByTask(task.getTaskId());
		assertEquals(2, changes.size());
		com.aidevos.orchestrator.change.ChangeSet repaired = changes.get(0);
		assertEquals(com.aidevos.orchestrator.change.ChangeStatus.CREATED,
			repaired.getStatus());

		// Timeline: CI_FAILED -> REPAIR_STARTED -> REPAIR_SUCCESS.
		TimelineService timelineService = new TimelineService(auditRepository, planRunRepository,
			new JobStore(), new InMemoryExecutionRecordRepository(), new TaskManager(),
			taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		assertTrue(eventTypes.contains("CI_FAILED"), "missing ci failed: " + eventTypes);
		assertTrue(eventTypes.contains("REPAIR_STARTED"), "missing repair start: " + eventTypes);
		assertTrue(eventTypes.contains("REPAIR_SUCCESS"), "missing repair success: " + eventTypes);
	}

	private CommitRecord commitAndPush(String taskId) throws Exception {
		ChangeSet change = changeService.getChangesByTask(taskId).get(0);
		changeService.startReview(change.getChangeId());
		changeService.approve(change.getChangeId(), "user-1");
		CommitRecord commit = commitService.commit(change.getChangeId());
		assertEquals(CommitStatus.SUCCESS, commit.getStatus());
		RemoteBranchRecord push = remoteGitService.push(commit.getCommitId(), null);
		assertEquals(RemoteStatus.SUCCESS, push.getStatus());
		return commit;
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

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
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
		gitOut(directory, args);
	}

	private String gitOut(Path directory, String... args) throws Exception {
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
		return output.trim();
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
