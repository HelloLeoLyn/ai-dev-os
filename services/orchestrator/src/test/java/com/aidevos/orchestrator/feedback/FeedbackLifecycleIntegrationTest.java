package com.aidevos.orchestrator.feedback;

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
import com.aidevos.orchestrator.change.ChangeStatus;
import com.aidevos.orchestrator.change.InMemoryChangeRepository;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiProviderProperties;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.ci.InMemoryCiRepository;
import com.aidevos.orchestrator.ci.MockCiProvider;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.commit.CommitStatus;
import com.aidevos.orchestrator.commit.InMemoryCommitRepository;
import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;
import com.aidevos.orchestrator.controller.FeedbackController;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
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
import com.aidevos.orchestrator.repair.CiFailureAnalyzer;
import com.aidevos.orchestrator.repair.RepairCoordinator;
import com.aidevos.orchestrator.repair.RepairStatus;
import com.aidevos.orchestrator.repair.RepairTask;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * End-to-end pull request feedback loop: PR -> CI FAILED -> repair ->
 * ChangeSet CREATED -> human approve -> commit -> push -> re-check CI ->
 * CI SUCCESS. Asserts the FeedbackStatus machine, the FEEDBACK_* audit
 * events, the task timeline and the feedback API. Uses a fake codex script,
 * a fake test runner and the mock CI provider (no real GitHub/GitLab).
 */
class FeedbackLifecycleIntegrationTest {

	@TempDir
	Path tempDir;

	private Path repo;
	private Path bare;
	private RetryRunner runner;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private ChangeService changeService;
	private CommitService commitService;
	private RemoteGitService remoteGitService;
	private PullRequestService pullRequestService;
	private CiService ciService;
	private MockCiProvider mockCiProvider;
	private RepairCoordinator repairCoordinator;
	private PrFeedbackService feedbackService;
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
		WorkspaceService workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(),
			new ProcessGitCommandExecutor(commandExecutor));
		workspaceId = workspaceService.createWorkspace("project-x", repo.toString()).getWorkspaceId();

		CodexProperties codexProperties = new CodexProperties();
		codexProperties.setExecutable(executable("codex",
			"#!/usr/bin/env bash\nprintf 'fix\\n' >> a.txt\necho 'codex executed'\nexit 0\n").toString());
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
		runner = new RetryRunner();
		TestAgentService testAgentService = new TestAgentService(runner,
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			workspaceService, changeService);
		taskCenterService.setAgentCoordinatorService(coordinator);

		mockCiProvider = new MockCiProvider();
		repairCoordinator = new RepairCoordinator(taskCenterService, testAgentService,
			plannerService, codexExecutor, workspaceService, memoryService, auditService,
			changeService);
		ciService = new CiService(new InMemoryCiRepository(), mockCiProvider,
			new CiProviderProperties(), pullRequestService, commitService, repairCoordinator,
			new CiFailureAnalyzer(workspaceService, changeService), auditService);
		feedbackService = new PrFeedbackService(new InMemoryFeedbackRepository(),
			pullRequestService, commitService, remoteGitService, changeService, auditService);
		feedbackService.setCiService(ciService);
		ciService.setFeedbackService(feedbackService);
		repairCoordinator.setFeedbackService(feedbackService);
		changeService.setFeedbackService(feedbackService);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
	}

	@Test
	void shouldRunFullFeedbackLoopToCiSuccess() throws Exception {
		TaskRecord task = createApprovedTask();
		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);
		assertEquals(TaskStatus.FAILED, executed.getStatus());

		CommitRecord commit = commitAndPush(task.getTaskId());
		PullRequestRecord pullRequest = pullRequestService.createPullRequest(
			commit.getCommitId(), null);
		assertEquals(PullRequestStatus.OPEN, pullRequest.getStatus());

		// CI FAILED -> repair runs synchronously -> feedback waits for review.
		CiRunRecord first = ciService.check(pullRequest.getPullRequestId());
		assertEquals(CiStatus.RUNNING, first.getStatus());
		mockCiProvider.setStatus("pipeline-" + pullRequest.getPullRequestId(),
			CiStatus.FAILED);
		CiRunRecord failed = ciService.check(pullRequest.getPullRequestId());
		assertEquals(CiStatus.FAILED, failed.getStatus());

		PrFeedbackRecord feedback = feedbackService.getByTask(task.getTaskId()).get(0);
		assertEquals(FeedbackStatus.WAITING_REVIEW, feedback.getStatus());
		assertEquals(0, feedback.getRetryCount());
		assertEquals(failed.getCiRunId(), feedback.getCiRunId());
		assertEquals(pullRequest.getPullRequestId(), feedback.getPullRequestId());
		assertTrue(repairCoordinator.getByCiRun(failed.getCiRunId()).isPresent());
		RepairTask repair = repairCoordinator.getByCiRun(failed.getCiRunId()).orElseThrow();
		assertEquals(RepairStatus.SUCCESS, repair.getStatus());

		// Repair ChangeSet: CREATED and linked, waiting for human review.
		assertTrue(feedback.getChangeId().startsWith("change-"));
		ChangeSet repairChange = changeService.getChange(feedback.getChangeId()).orElseThrow();
		assertEquals(ChangeStatus.CREATED, repairChange.getStatus());
		assertEquals(workspaceId, repairChange.getWorkspaceId());
		assertTrue(Files.readString(repo.resolve("a.txt")).contains("fix"));

		// Review + approve: feedback commits, pushes and re-checks CI.
		changeService.startReview(repairChange.getChangeId());
		changeService.approve(repairChange.getChangeId(), "user-1");
		PrFeedbackRecord rechecking = feedbackService.get(feedback.getFeedbackId()).orElseThrow();
		assertEquals(FeedbackStatus.RECHECKING, rechecking.getStatus());
		ChangeSet committed = changeService.getChange(repairChange.getChangeId()).orElseThrow();
		assertEquals(ChangeStatus.COMMITTED, committed.getStatus());
		String newCommitHash = commitService.getCommitsByTask(task.getTaskId()).get(0)
			.getGitHash();
		CiRunRecord recheckRun = ciService.getByTask(task.getTaskId()).get(0);
		assertEquals(newCommitHash, recheckRun.getCommitHash());
		assertEquals(CiStatus.RUNNING, recheckRun.getStatus());

		// CI re-check succeeds: feedback SUCCESS.
		mockCiProvider.setStatus("pipeline-" + pullRequest.getPullRequestId(),
			CiStatus.SUCCESS);
		CiRunRecord finalRun = ciService.check(pullRequest.getPullRequestId(), newCommitHash);
		assertEquals(CiStatus.SUCCESS, finalRun.getStatus());
		PrFeedbackRecord completed = feedbackService.get(feedback.getFeedbackId()).orElseThrow();
		assertEquals(FeedbackStatus.SUCCESS, completed.getStatus());

		// Audit: full FEEDBACK_* chain with taskId.
		assertEvent(EventType.FEEDBACK_CREATED, task.getTaskId());
		assertEvent(EventType.FEEDBACK_REPAIRING, task.getTaskId());
		assertEvent(EventType.FEEDBACK_WAITING_REVIEW, task.getTaskId());
		assertEvent(EventType.FEEDBACK_PUSHED, task.getTaskId());
		assertEvent(EventType.FEEDBACK_RECHECKING, task.getTaskId());
		assertEvent(EventType.FEEDBACK_SUCCESS, task.getTaskId());
		assertEvent(EventType.CI_FAILED, task.getTaskId());
		assertEvent(EventType.REPAIR_STARTED, task.getTaskId());
		assertEvent(EventType.REPAIR_SUCCESS, task.getTaskId());
		assertEvent(EventType.CHANGE_CREATED, task.getTaskId());
		assertEvent(EventType.CHANGE_APPROVED, task.getTaskId());
		assertEvent(EventType.COMMIT_SUCCESS, task.getTaskId());
		assertEvent(EventType.REMOTE_PUSH_SUCCESS, task.getTaskId());
		assertEvent(EventType.CI_STARTED, task.getTaskId());
		assertEvent(EventType.CI_SUCCESS, task.getTaskId());

		// Timeline: the complete PR feedback chain.
		TimelineService timelineService = new TimelineService(auditRepository, planRunRepository,
			new JobStore(), new InMemoryExecutionRecordRepository(), new TaskManager(),
			taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		for (String expected : List.of("CI_FAILED", "REPAIR_STARTED", "REPAIR_SUCCESS",
			"CHANGE_CREATED", "CHANGE_APPROVED", "COMMIT_SUCCESS", "REMOTE_PUSH_SUCCESS",
			"CI_STARTED", "CI_SUCCESS")) {
			assertTrue(eventTypes.contains(expected), "missing " + expected + ": " + eventTypes);
		}

		// API: GET feedback, GET task feedback list, POST retry.
		MockMvc mockMvc = standaloneSetup(new FeedbackController(feedbackService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
		mockMvc.perform(get("/api/feedback/" + feedback.getFeedbackId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.taskId").value(task.getTaskId()))
			.andExpect(jsonPath("$.changeId").value(repairChange.getChangeId()))
			.andExpect(jsonPath("$.ciRunId").value(finalRun.getCiRunId()));
		mockMvc.perform(get("/api/tasks/" + task.getTaskId() + "/feedback"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("SUCCESS"));
		mockMvc.perform(post("/api/feedback/" + feedback.getFeedbackId() + "/retry"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	private void assertEvent(EventType type, String taskId) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type
			&& taskId.equals(event.taskId())), "missing audit event " + type);
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
		return taskCenterService.getTask(task.getTaskId()).orElseThrow();
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

	/** Fails the first test run, passes every later run (the repair re-verify). */
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
