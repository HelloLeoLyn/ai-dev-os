package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionEngineTest {

	@Test
	void shouldExecuteSuccessfullyWithLegacyAgentName() {
		AgentManager agentManager = createAgentManager();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager, executionRecordManager);
		TaskDefinition taskDefinition = createTask("planner");

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Simulated execution for task task-1: Create an implementation plan", result.getOutput());
		assertEquals("pending", taskDefinition.getStatus());

		assertEquals(1, executionRecordManager.getAll().size());
		ExecutionRecord record = executionRecordManager.getAll().get(0);
		assertNotNull(record.getId());
		assertEquals("task-1", record.getTaskId());
		assertEquals("planner", record.getAgentName());
		assertEquals("SUCCESS", record.getStatus());
		assertEquals(result.getMessage(), record.getMessage());
		assertEquals(result.getOutput(), record.getOutput());
		assertEquals(result.getArtifacts(), record.getArtifacts());
		assertNotNull(record.getExecutionId());
		assertNotNull(record.getStartedAt());
		assertNotNull(record.getCompletedAt());
	}

	@Test
	void shouldSelectExecutorForCodingCapability() {
		assertCapabilityExecution(List.of("coding"), "executor");
	}

	@Test
	void shouldSelectPlannerForAnalysisCapability() {
		assertCapabilityExecution(List.of("analysis"), "planner");
	}

	@Test
	void shouldCreateExecutionContextWithResolvedAgentName() {
		AgentManager agentManager = createAgentManager();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		CapturingExecutor capturingExecutor = new CapturingExecutor();
		ExecutorRegistry executorRegistry = new ExecutorRegistry(List.of(capturingExecutor));
		ExecutionEngine executionEngine = new ExecutionEngine(
			createAgentResolver(agentManager, executorRegistry),
			executionRecordManager);
		TaskDefinition taskDefinition = createTask(null);
		taskDefinition.setName("Plan implementation");
		taskDefinition.setRequiredCapabilities(List.of("coding"));
		taskDefinition.setParameters(Map.of("browser", Map.of("action", "snapshot"),
			"agentId", "task-must-not-override-agent"));

		executionEngine.execute(taskDefinition);

		ExecutionContext context = capturingExecutor.getContext();
		assertNotNull(context);
		assertEquals("task-1", context.getTaskId());
		assertEquals("Plan implementation", context.getTaskName());
		assertEquals("Create an implementation plan", context.getDescription());
		assertEquals("executor", context.getAgentName());
		assertEquals("external-executor", context.getParameters().get("agentId"));
		assertEquals(Map.of("action", "snapshot"), context.getParameters().get("browser"));
		assertEquals("Create an implementation plan", context.getInput());
		assertEquals(System.getProperty("user.dir"), context.getWorkspace());
	}

	@Test
	void shouldFailWhenCapabilityDoesNotMatch() {
		AgentManager agentManager = createAgentManager();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager, executionRecordManager);
		TaskDefinition taskDefinition = createTask(null);
		taskDefinition.setRequiredCapabilities(List.of("unknown"));

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertFalse(result.isSuccess());
		assertEquals("Agent not found for required capabilities: [unknown]", result.getMessage());
		assertNull(result.getOutput());
		assertEquals("pending", taskDefinition.getStatus());

		assertEquals(1, executionRecordManager.getAll().size());
		ExecutionRecord record = executionRecordManager.getAll().get(0);
		assertNotNull(record.getId());
		assertEquals("task-1", record.getTaskId());
		assertNull(record.getAgentName());
		assertEquals("FAILED", record.getStatus());
		assertEquals(result.getMessage(), record.getMessage());
		assertNull(record.getOutput());
	}

	@Test
	void shouldConvertExecutorExceptionAndCreateFailedRecord() {
		AgentResolver agentResolver = mock(AgentResolver.class);
		AgentExecutor agentExecutor = mock(AgentExecutor.class);
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		TaskDefinition taskDefinition = createTask("planner");

		AgentDefinition agentDefinition = createAgent("planner", List.of("analysis"));
		when(agentResolver.resolve(taskDefinition)).thenReturn(new ResolvedAgent(agentDefinition, agentExecutor));
		when(agentExecutor.getType()).thenReturn("mock");
		when(agentExecutor.execute(org.mockito.ArgumentMatchers.any(ExecutionContext.class)))
			.thenThrow(new IllegalStateException("executor unavailable"));
		ExecutionEngine executionEngine = new ExecutionEngine(agentResolver,
			executionRecordManager);

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertFalse(result.isSuccess());
		assertEquals("Executor mock failed: executor unavailable", result.getMessage());
		assertNull(result.getOutput());
		assertEquals(1, executionRecordManager.getAll().size());
		ExecutionRecord record = executionRecordManager.getAll().get(0);
		assertEquals("FAILED", record.getStatus());
		assertEquals(result.getMessage(), record.getMessage());
		ExecutionReport report = record.getReport();
		assertNotNull(report);
		assertEquals("task-1", report.getTaskId());
		assertEquals("planner", report.getAgentName());
		assertFalse(report.isSuccess());
		assertNull(report.getBeforeGitStatus());
		assertNull(report.getAfterGitDiff());
	}

	@Test
	void shouldPersistApprovalIdForSuccessfulExecution() {
		assertApprovalIdPersisted(false, "SUCCESS");
	}

	@Test
	void shouldPersistApprovalIdForFailedExecution() {
		assertApprovalIdPersisted(true, "FAILED");
	}

	private void assertApprovalIdPersisted(boolean fail, String expectedStatus) {
		AgentResolver agentResolver = mock(AgentResolver.class);
		AgentExecutor agentExecutor = mock(AgentExecutor.class);
		ExecutionRecordManager records = new ExecutionRecordManager();
		TaskDefinition task = createTask("planner");
		AgentDefinition agent = createAgent("planner", List.of("analysis"));
		when(agentResolver.resolve(task)).thenReturn(new ResolvedAgent(agent, agentExecutor));
		when(agentExecutor.getType()).thenReturn("codex");
		when(agentExecutor.execute(org.mockito.ArgumentMatchers.any(ExecutionContext.class)))
			.thenAnswer(invocation -> {
				ExecutionContext context = invocation.getArgument(0);
				context.getMetadata().put("approvalId", "approval-1");
				if (fail) {
					throw new IllegalStateException("codex failed");
				}
				ExecutionResult result = new ExecutionResult();
				result.setSuccess(true);
				result.setMessage("Task executed successfully");
				return result;
			});

		new ExecutionEngine(agentResolver, records).execute(task, "job-1");

		ExecutionRecord record = records.getAll().get(0);
		assertEquals(expectedStatus, record.getStatus());
		assertEquals("job-1", record.getJobId());
		assertEquals("approval-1", record.getApprovalId());
	}

	private void assertCapabilityExecution(List<String> requiredCapabilities, String expectedAgentName) {
		AgentManager agentManager = createAgentManager();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager, executionRecordManager);
		TaskDefinition taskDefinition = createTask(null);
		taskDefinition.setRequiredCapabilities(requiredCapabilities);

		ExecutionResult result = executionEngine.execute(taskDefinition);

		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals(1, executionRecordManager.getAll().size());
		assertEquals(expectedAgentName, executionRecordManager.getAll().get(0).getAgentName());
	}

	private ExecutionEngine createExecutionEngine(AgentManager agentManager,
			ExecutionRecordManager executionRecordManager) {
		ExecutorRegistry executorRegistry = new ExecutorRegistry(List.of(new MockAgentExecutor()));
		return new ExecutionEngine(createAgentResolver(agentManager, executorRegistry),
			executionRecordManager);
	}

	private AgentResolver createAgentResolver(AgentManager agentManager,
			ExecutorRegistry executorRegistry) {
		return new AgentResolver(agentManager, new AgentSelector(agentManager),
			new ExecutorManager(agentManager, executorRegistry));
	}

	private AgentManager createAgentManager() {
		AgentManager agentManager = new AgentManager();
		agentManager.register(createAgent("planner", List.of("analysis")));
		agentManager.register(createAgent("executor", List.of("coding", "git")));
		return agentManager;
	}

	private AgentDefinition createAgent(String name, List<String> capabilities) {
		AgentDefinition agentDefinition = new AgentDefinition();
		agentDefinition.setName(name);
		agentDefinition.setExecutor("mock");
		agentDefinition.setExecutorConfig(Map.of("agentId", "external-" + name));
		agentDefinition.setCapabilities(capabilities);
		return agentDefinition;
	}

	private TaskDefinition createTask(String agentName) {
		TaskDefinition taskDefinition = new TaskDefinition();
		taskDefinition.setId("task-1");
		taskDefinition.setDescription("Create an implementation plan");
		taskDefinition.setAgentName(agentName);
		taskDefinition.setStatus("pending");
		return taskDefinition;
	}

	private static class CapturingExecutor implements AgentExecutor {

		private ExecutionContext context;

		@Override
		public String getType() {
			return "mock";
		}

		@Override
		public ExecutionResult execute(ExecutionContext context) {
			this.context = context;
			ExecutionResult result = new ExecutionResult();
			result.setSuccess(true);
			result.setMessage("Task executed successfully");
			return result;
		}

		ExecutionContext getContext() {
			return context;
		}
	}
}
