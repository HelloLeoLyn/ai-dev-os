package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentSelector;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
			new ExecutorManager(agentManager, executorRegistry),
			executionRecordManager, new AgentSelector(agentManager));
		TaskDefinition taskDefinition = createTask("legacy-agent");
		taskDefinition.setName("Plan implementation");
		taskDefinition.setRequiredCapabilities(List.of("coding"));

		executionEngine.execute(taskDefinition);

		ExecutionContext context = capturingExecutor.getContext();
		assertNotNull(context);
		assertEquals("task-1", context.getTaskId());
		assertEquals("Plan implementation", context.getTaskName());
		assertEquals("Create an implementation plan", context.getDescription());
		assertEquals("executor", context.getAgentName());
		assertEquals("Create an implementation plan", context.getInput());
		assertEquals(System.getProperty("user.dir"), context.getWorkspace());
	}

	@Test
	void shouldFailWhenCapabilityDoesNotMatch() {
		AgentManager agentManager = createAgentManager();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager, executionRecordManager);
		TaskDefinition taskDefinition = createTask("planner");
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

	private void assertCapabilityExecution(List<String> requiredCapabilities, String expectedAgentName) {
		AgentManager agentManager = createAgentManager();
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		ExecutionEngine executionEngine = createExecutionEngine(agentManager, executionRecordManager);
		TaskDefinition taskDefinition = createTask("legacy-agent");
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
		return new ExecutionEngine(new ExecutorManager(agentManager, executorRegistry),
			executionRecordManager, new AgentSelector(agentManager));
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
