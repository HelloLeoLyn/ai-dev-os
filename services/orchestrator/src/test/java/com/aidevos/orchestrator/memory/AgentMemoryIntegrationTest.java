package com.aidevos.orchestrator.memory;

import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.agentcapability.AgentCapabilityResolver;
import com.aidevos.orchestrator.agentcoordinator.AgentCoordinatorService;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
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
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestCommandResult;
import com.aidevos.orchestrator.testagent.TestCommandRunner;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.AgentExecutor;
import com.aidevos.orchestrator.orchestration.CodexAgentExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionGraphBuilder;
import com.aidevos.orchestrator.orchestration.ExecutionGraphExecutor;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.orchestration.HermesAgentExecutor;
import com.aidevos.orchestrator.orchestration.TestAgentExecutor;
import com.aidevos.orchestrator.memory.search.MemoryRankingService;
import com.aidevos.orchestrator.memory.search.MemorySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 16-B verification: Task -> Memory Search -> ExecutionGraph -> Agent
 * Context. Seeds historical experience, runs a task through the graph and
 * asserts the memory hints reach the agent context plus the MEMORY_*
 * audit events.
 */
class AgentMemoryIntegrationTest {

	private InMemoryMemoryRepository memoryRepository;
	private InMemoryAuditRepository auditRepository;
	private TaskCenterService taskCenterService;
	private AgentCoordinatorService coordinator;
	private RecordingHermes hermes;

	@BeforeEach
	void setUp() {
		PlannerService plannerService = mock(PlannerService.class);
		PlanApprovalService approvalService = mock(PlanApprovalService.class);
		PlanRunRepository planRunRepository = mock(PlanRunRepository.class);
		ModelRouterService modelRouterService = mock(ModelRouterService.class);

		AgentManager agentManager = new AgentManager();
		agentManager.register(agent("planner", "mock", List.of("planning", "analysis")));
		agentManager.register(agent("coder", "mock", List.of("coding", "git")));
		agentManager.register(agent("tester", "mock", List.of("testing", "browser")));
		ExecutorManager executorManager = new ExecutorManager(agentManager,
			new ExecutorRegistry(List.of(new MockAgentExecutor())));

		auditRepository = new InMemoryAuditRepository();
		AuditService auditService = new AuditService(auditRepository);
		memoryRepository = new InMemoryMemoryRepository();
		MemoryService memoryService = new MemoryService(memoryRepository);
		MemorySearchService searchService = new MemorySearchService(memoryRepository,
			new MemoryRankingService());
		seedMemory();

		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository(), auditService);
		taskCenterService = new TaskCenterService(plannerService, approvalService,
			planRunRepository);
		TestAgentService testAgentService = new TestAgentService(new PassingRunner(),
			new FakeBrowserExecutor(), taskCenterService, auditService, memoryService);

		ExecutionGraphBuilder graphBuilder = new ExecutionGraphBuilder();
		hermes = new RecordingHermes();
		ExecutionGraphExecutor graphExecutor = new ExecutionGraphExecutor(List.of(
			hermes,
			new CodexAgentExecutor(executorManager, null, executionRecordManager, null,
				auditService),
			new TestAgentExecutor(testAgentService)), auditService, taskCenterService);
		coordinator = new AgentCoordinatorService(taskCenterService, modelRouterService,
			plannerService, executorManager, testAgentService, auditService,
			new AgentCapabilityResolver(agentManager), memoryService, executionRecordManager,
			null, null, graphBuilder, graphExecutor, searchService);

		when(modelRouterService.route(any(TaskType.class))).thenReturn(
			new ResolvedModel(TaskType.GENERAL, "openai", "OpenAI", "LLM", "gpt-4o", true));
		when(plannerService.createPlan(any(PlanningRequest.class))).thenReturn(
			PlanningResult.success("hermes", null,
				new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(),
					null, Instant.parse("2026-08-01T00:00:00Z"))));
		when(planRunRepository.findRunIdByApproval(any())).thenReturn(null);
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "task-1",
			new Plan("plan-1", 1, "goal", PlanStatus.DRAFT, List.of(), List.of(), null,
				Instant.parse("2026-08-01T00:00:00Z")), "hash",
			Instant.parse("2026-08-01T00:00:00Z"));
		approval.approve("user-1", Instant.parse("2026-08-01T00:05:00Z"));
		when(approvalService.create(any(), any())).thenReturn(approval);
		when(approvalService.get("approval-1")).thenReturn(approval);
	}

	@Test
	void shouldRetrieveMemoryBeforeExecutionAndInjectHints() {
		TaskRecord task = taskCenterService.createTask(new CreateTaskRequest(
			"修复 Spring Boot 事务失效", "修复 Spring Boot 事务失效问题", "修复 Spring Boot 事务失效问题",
			"hermes", "project-x", null));
		task = taskCenterService.getTask(task.getTaskId()).orElseThrow();
		assertEquals(TaskStatus.APPROVED, task.getStatus());

		seedMemory();

		ExecutionGraph graph = coordinator.executeGraph(task.getTaskId(),
			TaskType.CODE_GENERATION);

		// ExecutionGraph carries the memory context and completes normally.
		assertNotNull(graph.getMemoryContext());
		assertFalse(graph.getMemoryContext().getSolutions().isEmpty(),
			"historical solutions must be retrieved");
		assertFalse(graph.getMemoryContext().getRecommendations().isEmpty());
		assertTrue(graph.getNodes().stream().allMatch(
			node -> node.getStatus() == ExecutionNodeStatus.COMPLETED));

		// Agent context received the memory hints.
		assertNotNull(hermes.lastContext);
		assertNotNull(hermes.lastContext.getMemoryHints());
		assertFalse(hermes.lastContext.getMemoryHints().getWarnings().isEmpty());

		// Audit events carry the taskId with match metadata.
		assertEvent(EventType.MEMORY_SEARCHED, task.getTaskId());
		assertEvent(EventType.MEMORY_MATCHED, task.getTaskId());
		assertEvent(EventType.MEMORY_APPLIED, task.getTaskId());
		EventRecord matched = events().stream()
			.filter(event -> event.type() == EventType.MEMORY_MATCHED).findFirst().orElseThrow();
		assertTrue((Integer) matched.metadata().get("matchCount") > 0);
		assertFalse(((java.util.List<?>) matched.metadata().get("memoryIds")).isEmpty());
	}

	private void assertEvent(EventType type, String taskId) {
		assertTrue(events().stream().anyMatch(event -> event.type() == type
			&& taskId.equals(event.taskId())), "missing audit event " + type);
	}

	private List<EventRecord> events() {
		return auditRepository.query(EventQuery.all());
	}

	private void seedMemory() {
		memory("bug-1", MemoryType.BUG_RECORD, "bug:spring-transaction",
			"Spring Boot 事务失效 错误: 自调用导致事务代理失效", "检查 self invocation，改为代理调用", true);
		memory("bug-2", MemoryType.BUG_RECORD, "bug:spring-transaction-unresolved",
			"Spring Boot 事务失效 未解决: 嵌套事务回滚不生效", null, false);
		memory("exp-1", MemoryType.AGENT_EXPERIENCE, "experience:spring-transaction",
			"经验: 修复 Spring Boot 事务失效", "拆分为独立事务方法", null);
		memory("task-1", MemoryType.HISTORY_TASK, "history:task-spring",
			"历史任务: 处理 Spring Boot 事务失效问题", "使用 @Transactional 代理调用", null);
	}

	private void memory(String id, MemoryType type, String key, String content,
			String solution, Boolean resolved) {
		MemoryRecord record = new MemoryRecord();
		record.setId(id);
		record.setProjectId("project-x");
		record.setType(type);
		record.setKey(key);
		record.setContent(content);
		record.setSolution(solution);
		record.setResolved(resolved);
		record.setCreatedAt(Instant.parse("2026-07-20T00:00:00Z"));
		record.setUpdatedAt(record.getCreatedAt());
		memoryRepository.save(record);
	}

	private AgentDefinition agent(String name, String executor, List<String> capabilities) {
		AgentDefinition definition = new AgentDefinition();
		definition.setName(name);
		definition.setVersion("1.0.0");
		definition.setExecutor(executor);
		definition.setCapabilities(capabilities);
		return definition;
	}

	private static final class RecordingHermes implements AgentExecutor {

		private AgentExecutionContext lastContext;

		@Override
		public AgentType type() {
			return AgentType.HERMES;
		}

		@Override
		public AgentExecutionResult execute(AgentExecutionContext context) {
			lastContext = context;
			return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
				"Plan created from memory", null);
		}
	}

	private static final class PassingRunner implements TestCommandRunner {
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
