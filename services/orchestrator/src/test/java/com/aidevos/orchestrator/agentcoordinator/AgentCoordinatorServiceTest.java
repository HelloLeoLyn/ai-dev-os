package com.aidevos.orchestrator.agentcoordinator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.modelrouter.ModelRouterService;
import com.aidevos.orchestrator.modelrouter.ResolvedModel;
import com.aidevos.orchestrator.modelrouter.TaskType;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.planner.PlannerService;
import com.aidevos.orchestrator.planner.PlanningRequest;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestStatus;
import com.aidevos.orchestrator.testagent.TestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCoordinatorServiceTest {

	private TaskCenterService taskCenterService;
	private ModelRouterService modelRouterService;
	private PlannerService plannerService;
	private ExecutorManager executorManager;
	private TestAgentService testAgentService;
	private AuditService auditService;
	private AgentCoordinatorService service;

	@BeforeEach
	void setUp() {
		taskCenterService = mock(TaskCenterService.class);
		modelRouterService = mock(ModelRouterService.class);
		plannerService = mock(PlannerService.class);
		executorManager = mock(ExecutorManager.class);
		testAgentService = mock(TestAgentService.class);
		auditService = new AuditService(new InMemoryAuditRepository());
		service = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService);

		when(taskCenterService.getTask("task-1")).thenReturn(Optional.of(task()));
		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", null, List.of(), List.of(), null, Instant.now())));
		when(testAgentService.createTest(any(CreateTestRequest.class))).thenReturn(successfulTest());
	}

	@Test
	void shouldSelectAgentPerTaskType() {
		assertEquals("hermes", service.selectAgent(TaskType.TASK_ANALYSIS));
		assertEquals("codex", service.selectAgent(TaskType.CODE_GENERATION));
		assertEquals("openclaw", service.selectAgent(TaskType.BROWSER_TEST));
		assertEquals("testagent", service.selectAgent(TaskType.TEST_VERIFY));
		assertEquals("hermes", service.selectAgent(TaskType.GENERAL));
		assertEquals("hermes", service.selectAgent(null));
	}

	@Test
	void shouldRunFullPipelineForTaskAnalysis() {
		stubExecutors(success());

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.TASK_ANALYSIS);

		assertEquals(List.of("hermes", "codex", "openclaw", "testagent"),
			steps.stream().map(AgentExecutionPlan::getAgentId).toList());
		assertEquals(List.of(1, 2, 3, 4),
			steps.stream().map(AgentExecutionPlan::getStep).toList());
		assertEquals(steps.getFirst().getPlanId(), steps.get(1).getPlanId());
		assertTrue(steps.stream().allMatch(step -> step.getStatus() == AgentPlanStatus.SUCCESS));
		assertTrue(steps.stream().allMatch(step -> step.getResult() != null));
	}

	@Test
	void shouldStartChainAtMappedAgentForCodeGeneration() {
		stubExecutors(success());

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.CODE_GENERATION);

		assertEquals(List.of("codex", "openclaw", "testagent"),
			steps.stream().map(AgentExecutionPlan::getAgentId).toList());
		assertTrue(steps.stream().allMatch(step -> step.getStatus() == AgentPlanStatus.SUCCESS));
	}

	@Test
	void shouldStartChainAtMappedAgentForBrowserTestAndVerify() {
		stubExecutors(success());

		List<AgentExecutionPlan> browser = service.createCollaborationPlan("task-1",
			TaskType.BROWSER_TEST);
		assertEquals(List.of("openclaw", "testagent"),
			browser.stream().map(AgentExecutionPlan::getAgentId).toList());

		List<AgentExecutionPlan> verify = service.createCollaborationPlan("task-1",
			TaskType.TEST_VERIFY);
		assertEquals(List.of("testagent"),
			verify.stream().map(AgentExecutionPlan::getAgentId).toList());
	}

	@Test
	void shouldStopAtFirstFailedStep() {
		stubExecutors(failure());

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.TASK_ANALYSIS);

		assertEquals(AgentPlanStatus.SUCCESS, steps.get(0).getStatus());
		assertEquals(AgentPlanStatus.FAILED, steps.get(1).getStatus());
		assertEquals("execution boom", steps.get(1).getResult());
		assertEquals(AgentPlanStatus.PENDING, steps.get(2).getStatus());
		assertEquals(AgentPlanStatus.PENDING, steps.get(3).getStatus());
	}

	@Test
	void shouldRejectUnknownTask() {
		when(taskCenterService.getTask("missing")).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
			() -> service.createCollaborationPlan("missing", TaskType.GENERAL));
	}

	@Test
	void shouldReturnPlanForTaskAndEmptyForUnknown() {
		stubExecutors(success());
		service.createCollaborationPlan("task-1", TaskType.TEST_VERIFY);

		List<AgentExecutionPlan> stored = service.getCollaborationPlan("task-1").orElseThrow();
		assertEquals(List.of("testagent"),
			stored.stream().map(AgentExecutionPlan::getAgentId).toList());
		assertTrue(service.getCollaborationPlan("missing").isEmpty());
	}

	@Test
	void shouldRecordAuditEvents() {
		stubExecutors(success());

		service.createCollaborationPlan("task-1", TaskType.TEST_VERIFY);

		List<EventRecord> events = auditService.query(EventQuery.all());
		assertTrue(events.stream().anyMatch(
			event -> event.type() == EventType.AGENT_PLAN_CREATED));
		assertTrue(events.stream().anyMatch(
			event -> event.type() == EventType.AGENT_PLAN_STARTED));
		assertTrue(events.stream().anyMatch(
			event -> event.type() == EventType.AGENT_PLAN_SUCCEEDED));
	}

	private void stubExecutors(ExecutionResult result) {
		AgentExecutor codex = mock(AgentExecutor.class);
		AgentExecutor openclaw = mock(AgentExecutor.class);
		when(codex.execute(any())).thenReturn(result);
		when(openclaw.execute(any())).thenReturn(result);
		when(executorManager.getExecutor("coder")).thenReturn(codex);
		when(executorManager.getExecutor("tester")).thenReturn(openclaw);
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

	private TaskRecord task() {
		return new TaskRecord("task-1", "Login flow", "Implement login flow");
	}

	private TestPlan successfulTest() {
		TestPlan plan = new TestPlan("test-1", "task-1", TestType.UNIT_TEST, "mvn test",
			"default", null);
		plan.markRunning();
		plan.markSuccess("exit code 0", "BUILD SUCCESS");
		return plan;
	}
}
