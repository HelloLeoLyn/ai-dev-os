package com.aidevos.orchestrator.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.ExecutorManager;
import com.aidevos.orchestrator.executor.ExecutorRegistry;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.plan.schedule.StepTaskFactory;
import com.aidevos.orchestrator.taskcenter.CreateTaskRequest;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRoutingLineageTest {

	@Test
	void createTaskRequestCarriesRequestedModelIdIntoTaskRecord() {
		CreateTaskRequest request = new CreateTaskRequest("Implement", "desc", "goal",
			"hermes", "project-1", "workspace-1", ExecutionMode.READ_WRITE, "deepseek-v4-flash");
		TaskRecord task = new TaskRecord("task-1", request.name(), request.description(),
			request.projectId(), request.workspaceId(), request.executionMode(), null,
			request.requestedModelId());

		assertEquals("deepseek-v4-flash", task.getRequestedModelId());
	}

	@Test
	void oldTaskWithoutRequestedModelStaysCompatible() {
		CreateTaskRequest request = new CreateTaskRequest("Implement", "desc", "goal",
			"hermes", "project-1", "workspace-1", ExecutionMode.READ_WRITE);
		TaskRecord task = new TaskRecord("task-1", request.name(), request.description(),
			request.projectId(), request.workspaceId(), request.executionMode());

		assertNull(task.getRequestedModelId());
	}

	@Test
	void stepTaskFactoryPropagatesRequestedModelFromPlanSnapshot() {
		PlanSnapshot snapshot = new PlanSnapshot(List.of(), java.util.Set.of(), List.of(),
			java.util.Set.of(), "v1",
			Map.of("projectId", "project-1", "workspaceId", "workspace-1",
				"workspacePath", "/workspace/project", "executionMode", "READ_WRITE",
				"requestedModelId", "deepseek-v4-flash"));
		PlanStep step = new PlanStep("step-1", "Implement", "Write code", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding", "git"), List.of()), null, null,
			Map.of(), List.of(), null, null, false);
		Plan plan = new Plan("plan-1", 1, "Implement", PlanStatus.DRAFT, List.of(step),
			List.of(), snapshot, Instant.now());
		PlanRun run = new PlanRun("run-1", "approval-1", "task-1", plan, List.of(),
			Instant.now());
		StepRun stepRun = new StepRun("step-run-1", "step-1");
		StepAttempt attempt = stepRun.startAttempt("attempt-1", Instant.now());

		TaskDefinition task = new StepTaskFactory().create(run, step, stepRun, attempt);

		assertEquals("deepseek-v4-flash", task.getParameters().get("requestedModelId"));
	}

	@Test
	void executionContextKeepsRequestedModelAndBlankAgentModelDoesNotClobber() {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		task.setDescription("Write code");
		task.setAgentName("coder");
		task.setRequiredCapabilities(List.of("coding", "git"));
		task.setParameters(Map.of("requestedModelId", "deepseek-v4-flash",
			"model", "resolved-by-executor"));

		AgentDefinition agent = new AgentDefinition();
		agent.setName("coder");
		agent.setExecutor("codex");
		agent.setExecutorConfig(Map.of("model", "", "sandbox", "workspace-write"));
		agent.setCapabilities(List.of("coding", "git"));

		AgentExecutor capturing = new CapturingExecutor();
		AgentResolver resolver = resolver(agent, capturing);
		ExecutionResult result = new ExecutionEngine(resolver, new ExecutionRecordManager())
			.execute(task);

		assertTrue(result.isSuccess());
		ExecutionContext context = ((CapturingExecutor) capturing).context();
		assertEquals("deepseek-v4-flash", context.getParameters().get("requestedModelId"));
		assertEquals("resolved-by-executor", context.getParameters().get("model"));
		assertFalse(context.getParameters().containsKey("agentDefaultModelId"));
	}

	@Test
	void agentDefaultModelIsExposedAsAgentDefaultModelIdWithoutOverwritingTaskValues() {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		task.setDescription("Write code");
		task.setAgentName("coder");
		task.setRequiredCapabilities(List.of("coding", "git"));
		task.setParameters(Map.of("requestedModelId", "deepseek-v4-flash"));

		AgentDefinition agent = new AgentDefinition();
		agent.setName("coder");
		agent.setExecutor("codex");
		agent.setExecutorConfig(Map.of("model", "gpt-5.4", "sandbox", "workspace-write"));
		agent.setCapabilities(List.of("coding", "git"));

		AgentExecutor capturing = new CapturingExecutor();
		AgentResolver resolver = resolver(agent, capturing);
		new ExecutionEngine(resolver, new ExecutionRecordManager()).execute(task);

		ExecutionContext context = ((CapturingExecutor) capturing).context();
		assertEquals("deepseek-v4-flash", context.getParameters().get("requestedModelId"));
		assertEquals("gpt-5.4", context.getParameters().get("agentDefaultModelId"));
		assertFalse(context.getParameters().containsKey("model"));
	}

	private AgentResolver resolver(AgentDefinition agent, AgentExecutor executor) {
		AgentManager manager = new AgentManager();
		manager.register(agent);
		ExecutorRegistry registry = new ExecutorRegistry(List.of(executor));
		return new AgentResolver(manager, new AgentSelector(manager),
			new ExecutorManager(manager, registry));
	}

	private static class CapturingExecutor implements AgentExecutor {

		private ExecutionContext captured;

		@Override
		public String getType() {
			return "codex";
		}

		@Override
		public ExecutionResult execute(ExecutionContext context) {
			this.captured = context;
			ExecutionResult result = new ExecutionResult();
			result.setSuccess(true);
			result.setMessage("Task executed successfully");
			return result;
		}

		ExecutionContext context() {
			return captured;
		}
	}
}
