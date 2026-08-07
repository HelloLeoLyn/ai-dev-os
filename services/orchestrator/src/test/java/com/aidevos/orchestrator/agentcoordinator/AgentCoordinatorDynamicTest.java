package com.aidevos.orchestrator.agentcoordinator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.manager.AgentManager;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the coordinator resolves agents dynamically through the capability
 * registry instead of hard-coded agent names.
 */
class AgentCoordinatorDynamicTest {

	private TaskCenterService taskCenterService;
	private ModelRouterService modelRouterService;
	private PlannerService plannerService;
	private ExecutorManager executorManager;
	private TestAgentService testAgentService;
	private AuditService auditService;
	private AgentManager agentManager;
	private AgentCoordinatorService service;
	private AgentExecutor coderExecutor;
	private AgentExecutor browserExecutor;
	private AgentExecutor testerExecutor;

	@BeforeEach
	void setUp() {
		taskCenterService = mock(TaskCenterService.class);
		modelRouterService = mock(ModelRouterService.class);
		plannerService = mock(PlannerService.class);
		executorManager = mock(ExecutorManager.class);
		testAgentService = mock(TestAgentService.class);
		auditService = new AuditService(new InMemoryAuditRepository());
		agentManager = new AgentManager();
		registerAgents();
		service = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager));

		when(taskCenterService.getTask("task-1")).thenReturn(Optional.of(task()));
		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", null, List.of(), List.of(), null, Instant.now())));
		when(testAgentService.createTest(any(CreateTestRequest.class))).thenReturn(
			successfulTest());

		coderExecutor = mock(AgentExecutor.class);
		browserExecutor = mock(AgentExecutor.class);
		testerExecutor = mock(AgentExecutor.class);
		when(coderExecutor.execute(any())).thenReturn(success());
		when(browserExecutor.execute(any())).thenReturn(success());
		when(testerExecutor.execute(any())).thenReturn(success());
		when(executorManager.getExecutor("coder")).thenReturn(coderExecutor);
		when(executorManager.getExecutor("browser-agent")).thenReturn(browserExecutor);
		when(executorManager.getExecutor("tester")).thenReturn(testerExecutor);
	}

	@Test
	void shouldResolveChainAgentsByCapability() {
		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.TASK_ANALYSIS);

		assertEquals(List.of("planner", "coder", "browser-agent", "tester"),
			agentIds(steps));
		assertEquals(List.of("planning", "coding", "browser", "testing"),
			steps.stream().map(AgentExecutionPlan::getCapability).toList());
		assertTrue(steps.stream().allMatch(step -> step.getStatus() == AgentPlanStatus.SUCCESS));
	}

	@Test
	void shouldStartChainAtMappedCapability() {
		List<AgentExecutionPlan> code = service.createCollaborationPlan("task-1",
			TaskType.CODE_GENERATION);
		assertEquals(List.of("coder", "browser-agent", "tester"), agentIds(code));

		List<AgentExecutionPlan> browser = service.createCollaborationPlan("task-1",
			TaskType.BROWSER_TEST);
		assertEquals(List.of("browser-agent", "tester"), agentIds(browser));

		List<AgentExecutionPlan> verify = service.createCollaborationPlan("task-1",
			TaskType.TEST_VERIFY);
		assertEquals(List.of("tester"), agentIds(verify));
	}

	@Test
	void shouldDispatchEachCapabilityToItsExecutor() {
		service.createCollaborationPlan("task-1", TaskType.TASK_ANALYSIS);

		verify(plannerService).createPlan(any(PlanningRequest.class));
		verify(coderExecutor).execute(any(ExecutionContext.class));
		verify(browserExecutor).execute(any(ExecutionContext.class));
		verify(testAgentService).createTest(any(CreateTestRequest.class));
	}

	@Test
	void shouldSelectHigherVersionAgentForSameCapability() {
		agentManager.register(agent("coder-pro", "2.0.0", List.of("coding"), "codex"));
		when(executorManager.getExecutor("coder-pro")).thenReturn(coderExecutor);

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.CODE_GENERATION);

		assertEquals("coder-pro", steps.getFirst().getAgentId());
		assertTrue(steps.getFirst().getStatus() == AgentPlanStatus.SUCCESS);
	}

	@Test
	void shouldSkipDisabledAgents() {
		agentManager.register(agent("coder-disabled", "9.9.9", List.of("coding"), "codex", false));

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.CODE_GENERATION);

		assertEquals("coder", steps.getFirst().getAgentId());
	}

	@Test
	void shouldStopAtFirstFailedStep() {
		when(coderExecutor.execute(any())).thenReturn(failure());

		List<AgentExecutionPlan> steps = service.createCollaborationPlan("task-1",
			TaskType.TASK_ANALYSIS);

		assertEquals(AgentPlanStatus.SUCCESS, steps.get(0).getStatus());
		assertEquals(AgentPlanStatus.FAILED, steps.get(1).getStatus());
		assertEquals(AgentPlanStatus.PENDING, steps.get(2).getStatus());
		assertEquals(AgentPlanStatus.PENDING, steps.get(3).getStatus());
	}

	@Test
	void shouldRejectPlanWhenNoAgentProvidesCapability() {
		AgentManager emptyManager = new AgentManager();
		AgentCoordinatorService emptyService = new AgentCoordinatorService(taskCenterService,
			modelRouterService, plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(emptyManager));

		assertThrows(IllegalStateException.class,
			() -> emptyService.createCollaborationPlan("task-1", TaskType.TEST_VERIFY));
	}

	private void registerAgents() {
		agentManager.register(agent("planner", "1.0.0", List.of("planning", "analysis"), "mock"));
		agentManager.register(agent("executor", "1.0.0", List.of("coding", "git"), "mock"));
		agentManager.register(agent("coder", "1.0.0", List.of("coding", "git"), "codex"));
		agentManager.register(agent("tester", "1.0.0", List.of("testing", "browser"), "openclaw"));
		agentManager.register(agent("browser-agent", "1.0.0", List.of("browser"), "openclaw"));
	}

	private AgentDefinition agent(String name, String version, List<String> capabilities,
			String executor) {
		return agent(name, version, capabilities, executor, true);
	}

	private AgentDefinition agent(String name, String version, List<String> capabilities,
			String executor, boolean enabled) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		definition.setVersion(version);
		definition.setCapabilities(capabilities);
		definition.setExecutor(executor);
		definition.setEnabled(enabled);
		return definition;
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
