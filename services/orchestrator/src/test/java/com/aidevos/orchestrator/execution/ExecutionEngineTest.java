package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.executor.MockAgentExecutor;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitResult;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
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
			executionRecordManager, new AgentSelector(agentManager), createGitExecutor());
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

	@Test
	void shouldRecordGitStatusBeforeExecutionAndGitDiffAfterExecution() {
		ExecutorManager executorManager = mock(ExecutorManager.class);
		AgentExecutor agentExecutor = mock(AgentExecutor.class);
		GitExecutor gitExecutor = mock(GitExecutor.class);
		ExecutionRecordManager executionRecordManager = new ExecutionRecordManager();
		TaskDefinition taskDefinition = createTask("planner");
		ExecutionResult executionResult = new ExecutionResult();
		executionResult.setSuccess(true);
		executionResult.setMessage("Task executed successfully");
		executionResult.setOutput("Agent output");
		GitResult statusResult = successfulGitResult(" M README.md");
		GitResult diffResult = successfulGitResult(" README.md | 2 ++");
		String workspace = System.getProperty("user.dir");

		when(executorManager.getExecutor("planner")).thenReturn(agentExecutor);
		when(gitExecutor.status(workspace)).thenReturn(statusResult);
		when(agentExecutor.execute(org.mockito.ArgumentMatchers.any(ExecutionContext.class)))
			.thenReturn(executionResult);
		when(gitExecutor.diff(workspace)).thenReturn(diffResult);
		ExecutionEngine executionEngine = new ExecutionEngine(executorManager,
			executionRecordManager, mock(AgentSelector.class), gitExecutor);

		ExecutionResult result = executionEngine.execute(taskDefinition);

		InOrder executionOrder = inOrder(gitExecutor, agentExecutor);
		executionOrder.verify(gitExecutor).status(workspace);
		executionOrder.verify(agentExecutor).execute(
			org.mockito.ArgumentMatchers.any(ExecutionContext.class));
		executionOrder.verify(gitExecutor).diff(workspace);
		assertEquals(executionResult, result);

		ExecutionReport report = executionRecordManager.getAll().get(0).getReport();
		assertNotNull(report);
		assertEquals("task-1", report.getTaskId());
		assertEquals("planner", report.getAgentName());
		assertTrue(report.isSuccess());
		assertEquals(" M README.md", report.getBeforeGitStatus());
		assertEquals(" README.md | 2 ++", report.getAfterGitDiff());
		assertEquals("Agent output", report.getOutput());
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
			executionRecordManager, new AgentSelector(agentManager), createGitExecutor());
	}

	private GitExecutor createGitExecutor() {
		GitExecutor gitExecutor = mock(GitExecutor.class);
		when(gitExecutor.status(org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(successfulGitResult(""));
		when(gitExecutor.diff(org.mockito.ArgumentMatchers.anyString()))
			.thenReturn(successfulGitResult(""));
		return gitExecutor;
	}

	private static GitResult successfulGitResult(String output) {
		GitResult result = new GitResult();
		result.setSuccess(true);
		result.setOutput(output);
		return result;
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
