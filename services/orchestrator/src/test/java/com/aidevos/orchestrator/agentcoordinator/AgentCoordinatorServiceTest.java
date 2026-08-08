package com.aidevos.orchestrator.agentcoordinator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.model.AgentDefinition;
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
	private AgentManager agentManager;
	private MemoryService memoryService;
	private ExecutionRecordManager executionRecordManager;
	private AgentCoordinatorService service;

	@BeforeEach
	void setUp() {
		taskCenterService = mock(TaskCenterService.class);
		modelRouterService = mock(ModelRouterService.class);
		plannerService = mock(PlannerService.class);
		executorManager = mock(ExecutorManager.class);
		testAgentService = mock(TestAgentService.class);
		auditService = new AuditService(new InMemoryAuditRepository());
		memoryService = new MemoryService(new InMemoryMemoryRepository());
		executionRecordManager = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository(), auditService);
		agentManager = new AgentManager();
		registerAgents();
		service = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager);

		when(taskCenterService.getTask("task-1")).thenReturn(Optional.of(task()));
		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", null, List.of(), List.of(), null, Instant.now())));
		when(testAgentService.createTest(any(CreateTestRequest.class))).thenReturn(
			successfulTest());
	}

	@Test
	void shouldSelectAgentPerTaskType() {
		assertEquals("planner", service.selectAgent(TaskType.TASK_ANALYSIS));
		assertEquals("coder", service.selectAgent(TaskType.CODE_GENERATION));
		assertEquals("browser-agent", service.selectAgent(TaskType.BROWSER_TEST));
		assertEquals("tester", service.selectAgent(TaskType.TEST_VERIFY));
		assertEquals("planner", service.selectAgent(TaskType.GENERAL));
		assertEquals("planner", service.selectAgent(null));
	}

	@Test
	void shouldRunFullPipelineForTaskAnalysis() {
		stubExecutors(success());

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.TASK_ANALYSIS);

		assertEquals(List.of("planner", "coder", "browser-agent", "tester"),
			agentIds(steps));
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

		assertEquals(List.of("coder", "browser-agent", "tester"), agentIds(steps));
		assertTrue(steps.stream().allMatch(step -> step.getStatus() == AgentPlanStatus.SUCCESS));
	}

	@Test
	void shouldStartChainAtMappedAgentForBrowserTestAndVerify() {
		stubExecutors(success());

		List<AgentExecutionPlan> browser = service.createCollaborationPlan("task-1",
			TaskType.BROWSER_TEST);
		assertEquals(List.of("browser-agent", "tester"), agentIds(browser));

		List<AgentExecutionPlan> verify = service.createCollaborationPlan("task-1",
			TaskType.TEST_VERIFY);
		assertEquals(List.of("tester"), agentIds(verify));
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
		assertEquals(List.of("tester"), agentIds(stored));
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
		AgentExecutor coder = mock(AgentExecutor.class);
		AgentExecutor browser = mock(AgentExecutor.class);
		AgentExecutor tester = mock(AgentExecutor.class);
		when(coder.execute(any())).thenReturn(result);
		when(browser.execute(any())).thenReturn(result);
		when(tester.execute(any())).thenReturn(result);
		when(executorManager.getExecutor("coder")).thenReturn(coder);
		when(executorManager.getExecutor("browser-agent")).thenReturn(browser);
		when(executorManager.getExecutor("tester")).thenReturn(tester);
	}

	private List<String> agentIds(List<AgentExecutionPlan> steps) {
		return steps.stream().map(AgentExecutionPlan::getAgentId).toList();
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
