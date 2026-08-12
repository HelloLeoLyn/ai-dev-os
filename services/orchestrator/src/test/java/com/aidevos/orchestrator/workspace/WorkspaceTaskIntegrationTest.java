package com.aidevos.orchestrator.workspace;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.agentcoordinator.AgentExecutionPlan;
import com.aidevos.orchestrator.agentcoordinator.AgentPlanStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
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
import com.aidevos.orchestrator.project.InMemoryProjectRepository;
import com.aidevos.orchestrator.project.Project;
import com.aidevos.orchestrator.project.ProjectService;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Project -> Workspace -> Task -> AgentExecutionPlan chain: a
 * workspace created for a project is carried from the task through the agent
 * coordinator collaboration plan without changing the closed-loop behavior.
 */
class WorkspaceTaskIntegrationTest {

	@TempDir
	Path tempDir;

	private ProjectService projectService;
	private WorkspaceService workspaceService;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private PlannerService plannerService;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private ModelRouterService modelRouterService;
	private ExecutorManager executorManager;
	private AgentExecutor coderExecutor;
	private AgentExecutor browserExecutor;

	@BeforeEach
	void setUp() {
		projectService = new ProjectService(new InMemoryProjectRepository());
		GitCommandExecutor gitCommandExecutor = mock(GitCommandExecutor.class);
		when(gitCommandExecutor.status(any())).thenReturn(
			new com.aidevos.orchestrator.workspace.git.GitStatus("main", 0, 0, 0));
		workspaceService = new WorkspaceService(new InMemoryWorkspaceRepository(),
			gitCommandExecutor);

		plannerService = mock(PlannerService.class);
		approvalService = mock(PlanApprovalService.class);
		planRunRepository = mock(PlanRunRepository.class);
		modelRouterService = mock(ModelRouterService.class);
		executorManager = mock(ExecutorManager.class);

		AgentManagerStub agents = new AgentManagerStub();
		agents.register("planner", List.of("planning", "analysis"), "mock");
		agents.register("coder", List.of("coding", "git"), "codex");
		agents.register("tester", List.of("testing", "browser"), "openclaw");
		agents.register("browser-agent", List.of("browser"), "openclaw");
		stubExecutors(success());

		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
		MemoryService memoryService = new MemoryService(memoryRepository);
		InMemoryExecutionRecordRepository recordRepository = new InMemoryExecutionRecordRepository();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			recordRepository, auditService);

		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		TestAgentService testAgentService = new TestAgentService(new FakeRunner(),
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agents.manager), memoryService, executionRecordManager);
		taskCenterService.setAgentCoordinatorService(coordinator);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
	}

	@Test
	void shouldCarryWorkspaceFromProjectThroughTaskIntoExecutionPlan() {
		Project project = projectService.createProject(createProjectRequest());
		String workspaceId = workspaceService.createWorkspace(project.getProjectId(),
			tempDir.toString()).getWorkspaceId();

		TaskRecord task = createApprovedTask(project.getProjectId(), workspaceId);
		assertEquals(workspaceId, task.getWorkspaceId());
		assertEquals(project.getProjectId(), task.getProjectId());

		TaskRecord executed = taskCenterService.execute(task.getTaskId(),
			TaskType.TASK_ANALYSIS);

		assertEquals(TaskStatus.COMPLETED, executed.getStatus());
		List<AgentExecutionPlan> steps = coordinator.getCollaborationPlan(task.getTaskId())
			.orElseThrow();
		assertEquals(4, steps.size());
		assertTrue(steps.stream().allMatch(step -> AgentPlanStatus.SUCCESS == step.getStatus()));
		assertTrue(steps.stream().allMatch(step -> task.getTaskId().equals(step.getTaskId())));
		assertTrue(steps.stream().allMatch(step -> workspaceId.equals(step.getWorkspaceId())),
			"every plan step must carry the task workspaceId");
		assertTrue(steps.stream().allMatch(step -> project.getProjectId().equals(step.getProjectId())),
			"every plan step must carry the task projectId");
		assertTrue(steps.stream().allMatch(step -> ExecutionMode.READ_ONLY == step.getExecutionMode()),
			"every plan step must carry READ_ONLY mode");
	}

	@Test
	void shouldExposeProjectWorkspaceLookup() {
		Project project = projectService.createProject(createProjectRequest());
		Workspace workspace = workspaceService.createWorkspace(project.getProjectId(),
			tempDir.toString());

		assertEquals(workspace.getWorkspaceId(), workspaceService
			.getProjectWorkspace(project.getProjectId()).orElseThrow().getWorkspaceId());
		assertEquals(project.getProjectId(), workspace.getProjectId());
		assertTrue(workspaceService.getWorkspace(workspace.getWorkspaceId()).isPresent());
	}

	private TaskRecord createApprovedTask(String projectId, String workspaceId) {
		Plan plan = new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.parse("2026-08-01T00:00:00Z"));
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1", plan,
			"hash", Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);

		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"Implement login", "Login flow", "Implement a login flow", "hermes",
			projectId, workspaceId, ExecutionMode.READ_ONLY));
		TaskRecord refreshed = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.APPROVED, refreshed.getStatus());
		return refreshed;
	}

	private com.aidevos.orchestrator.project.CreateProjectRequest createProjectRequest() {
		return new com.aidevos.orchestrator.project.CreateProjectRequest("demo",
			tempDir.toString(), "Demo project");
	}

	private void stubExecutors(ExecutionResult result) {
		coderExecutor = mock(AgentExecutor.class);
		browserExecutor = mock(AgentExecutor.class);
		when(coderExecutor.execute(any())).thenReturn(result);
		when(browserExecutor.execute(any())).thenReturn(result);
		when(executorManager.getExecutor("coder")).thenReturn(coderExecutor);
		when(executorManager.getExecutor("browser-agent")).thenReturn(browserExecutor);
	}

	private ExecutionResult success() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		result.setOutput("done");
		return result;
	}

	private static final class AgentManagerStub {

		private final com.aidevos.orchestrator.manager.AgentManager manager =
			new com.aidevos.orchestrator.manager.AgentManager();

		private void register(String name, List<String> capabilities, String executor) {
			AgentDefinition definition = new AgentDefinition();
			definition.setName(name);
			definition.setVersion("1.0.0");
			definition.setCapabilities(capabilities);
			definition.setExecutor(executor);
			manager.register(definition);
		}
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
