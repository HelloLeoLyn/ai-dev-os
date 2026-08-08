package com.aidevos.orchestrator.taskcenter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.agentcoordinator.AgentExecutionPlan;
import com.aidevos.orchestrator.agentcoordinator.AgentPlanStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
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
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.timeline.TimelineEventDTO;
import com.aidevos.orchestrator.timeline.TimelineService;
import com.aidevos.orchestrator.timeline.UnifiedTimeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 13-B-1 closed-loop verification: APPROVED task -> AgentCoordinator ->
 * collaboration plan -> coding -> testing -> task status / ExecutionRecord /
 * Memory / Audit / Timeline. Uses real services with in-memory repositories
 * and fake executors, so no real model, Codex CLI or Docker is involved.
 */
class TaskCenterClosedLoopTest {

	private PlannerService plannerService;
	private PlanApprovalService approvalService;
	private PlanRunRepository planRunRepository;
	private ModelRouterService modelRouterService;
	private ExecutorManager executorManager;
	private AgentManager agentManager;
	private AgentExecutor coderExecutor;
	private AgentExecutor browserExecutor;
	private InMemoryAuditRepository auditRepository;
	private AuditService auditService;
	private InMemoryMemoryRepository memoryRepository;
	private MemoryService memoryService;
	private InMemoryExecutionRecordRepository recordRepository;
	private ExecutionRecordManager executionRecordManager;
	private FakeRunner runner;
	private TestAgentService testAgentService;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;

	@BeforeEach
	void setUp() {
		plannerService = mock(PlannerService.class);
		approvalService = mock(PlanApprovalService.class);
		planRunRepository = mock(PlanRunRepository.class);
		modelRouterService = mock(ModelRouterService.class);
		executorManager = mock(ExecutorManager.class);

		agentManager = new AgentManager();
		registerAgents();
		stubExecutors(success());

		auditRepository = new InMemoryAuditRepository();
		auditService = new AuditService(auditRepository);
		memoryRepository = new InMemoryMemoryRepository();
		memoryService = new MemoryService(memoryRepository);
		recordRepository = new InMemoryExecutionRecordRepository();
		executionRecordManager = new ExecutionRecordManager(recordRepository, auditService);

		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		runner = new FakeRunner();
		testAgentService = new TestAgentService(runner, new FakeBrowserExecutor(),
			taskCenterService, auditService, memoryService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager);
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
	void shouldRunClosedLoopToCompletionAndPersistMemory() {
		TaskRecord task = createApprovedTask();

		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);

		assertEquals(TaskStatus.COMPLETED, executed.getStatus());
		List<AgentExecutionPlan> steps = coordinator.getCollaborationPlan(task.getTaskId())
			.orElseThrow();
		assertEquals(List.of("planner", "coder", "browser-agent", "tester"),
			steps.stream().map(AgentExecutionPlan::getAgentId).toList());
		assertTrue(steps.stream().allMatch(step -> step.getStatus() == AgentPlanStatus.SUCCESS));
		assertTrue(steps.stream().allMatch(step -> step.getResult() != null));

		// Memory: HISTORY_TASK written on success.
		List<MemoryRecord> history = memoryRepository.list("default", MemoryType.HISTORY_TASK);
		assertEquals(1, history.size());
		assertEquals("history:task:" + task.getTaskId(), history.getFirst().getKey());

		// Execution records for the coding and browser agents.
		List<ExecutionRecord> records = recordRepository.getAll();
		assertEquals(2, records.size());
		assertTrue(records.stream().allMatch(
			record -> task.getTaskId().equals(record.getTaskId())
				&& "SUCCESS".equals(record.getStatus())));

		// Audit: agent plan, execution and test events all carry the taskId.
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.AGENT_PLAN_SUCCEEDED));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.EXECUTION_RECORD_SAVED));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.TEST_SUCCEEDED));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.USER_OPERATION
			&& task.getTaskId().equals(event.taskId())));

		// Timeline: Task -> Plan -> Agent -> Execution -> Test -> Result.
		TimelineService timelineService = new TimelineService(auditRepository, planRunRepository,
			new JobStore(), recordRepository, new TaskManager(), taskCenterService);
		UnifiedTimeline timeline = timelineService.timeline(task.getTaskId());
		assertEquals("TASK", timeline.scopeType());
		List<String> eventTypes = timeline.events().stream()
			.map(TimelineEventDTO::eventType).toList();
		assertTrue(eventTypes.contains("USER_OPERATION"), "missing task event: " + eventTypes);
		assertTrue(eventTypes.contains("AGENT_PLAN_CREATED"), "missing plan event: " + eventTypes);
		assertTrue(eventTypes.contains("AGENT_PLAN_SUCCEEDED"), "missing agent event: " + eventTypes);
		assertTrue(eventTypes.contains("EXECUTION_RECORD_SAVED"), "missing execution event: " + eventTypes);
		assertTrue(eventTypes.contains("TEST_CREATED"), "missing test event: " + eventTypes);
	}

	@Test
	void shouldMarkFailedAndPersistBugRecordWhenCodingFails() {
		TaskRecord task = createApprovedTask();
		when(coderExecutor.execute(any())).thenReturn(failure());

		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);

		assertEquals(TaskStatus.FAILED, executed.getStatus());
		assertTrue(executed.getErrorMessage().contains("execution boom"));
		List<AgentExecutionPlan> steps = coordinator.getCollaborationPlan(task.getTaskId())
			.orElseThrow();
		assertEquals(AgentPlanStatus.FAILED, steps.get(1).getStatus());
		assertEquals(AgentPlanStatus.PENDING, steps.get(2).getStatus());
		assertTrue(memoryRepository.list("default", MemoryType.BUG_RECORD).stream()
			.anyMatch(record -> ("bug:task:" + task.getTaskId()).equals(record.getKey())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.AGENT_PLAN_FAILED));
	}

	@Test
	void shouldMarkFailedAndPersistBugRecordWhenTestsFail() {
		TaskRecord task = createApprovedTask();
		runner.fail();

		TaskRecord executed = taskCenterService.execute(task.getTaskId(), TaskType.TASK_ANALYSIS);

		assertEquals(TaskStatus.FAILED, executed.getStatus());
		List<AgentExecutionPlan> steps = coordinator.getCollaborationPlan(task.getTaskId())
			.orElseThrow();
		assertEquals(AgentPlanStatus.FAILED, steps.get(3).getStatus());
		assertTrue(memoryRepository.list("default", MemoryType.BUG_RECORD).stream()
			.anyMatch(record -> ("bug:task:" + task.getTaskId()).equals(record.getKey())));
		assertTrue(events().stream().anyMatch(event -> event.type() == EventType.TEST_FAILED));
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
			"Implement login", "Login flow", "Implement a login flow", "hermes", "default"));
		TaskRecord refreshed = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.APPROVED, refreshed.getStatus());
		return refreshed;
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	private void registerAgents() {
		agentManager.register(agent("planner", "1.0.0", List.of("planning", "analysis"), "mock"));
		agentManager.register(agent("coder", "1.0.0", List.of("coding", "git"), "codex"));
		agentManager.register(agent("tester", "1.0.0", List.of("testing", "browser"), "openclaw"));
		agentManager.register(agent("browser-agent", "1.0.0", List.of("browser"), "openclaw"));
	}

	private AgentDefinition agent(String name, String version, List<String> capabilities,
			String executor) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		definition.setVersion(version);
		definition.setCapabilities(capabilities);
		definition.setExecutor(executor);
		return definition;
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

	private ExecutionResult failure() {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(false);
		result.setMessage("execution boom");
		return result;
	}

	private static final class FakeRunner implements TestCommandRunner {

		private boolean fail;

		void fail() {
			this.fail = true;
		}

		@Override
		public TestCommandResult run(String command, String workdir) {
			if (fail) {
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
