package com.aidevos.orchestrator.plan.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.ResolvedAgent;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanExecutionRecordCorrelationTest {

	@Test
	void executionRecordShouldRetainPlanRunCorrelation() {
		AgentResolver resolver = mock(AgentResolver.class);
		AgentDefinition agent = new AgentDefinition();
		agent.setName("fake-agent");
		agent.setExecutor("fake");
		agent.setExecutorConfig(Map.of());
		when(resolver.resolve(any())).thenReturn(new ResolvedAgent(agent, executor()));
		ExecutionRecordManager records = new ExecutionRecordManager();
		ExecutionEngine engine = new ExecutionEngine(resolver, records);
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		task.setName("Task");
		task.setDescription("Execute");
		task.setAgentName("fake-agent");
		task.setRequiredCapabilities(List.of());
		task.setMetadata(Map.of("planRunId", "plan-run-1", "stepRunId", "step-run-1",
			"attemptId", "attempt-1"));

		engine.execute(task, "job-1");

		ExecutionRecord record = records.getAll().getFirst();
		assertEquals("plan-run-1", record.getPlanRunId());
		assertEquals("step-run-1", record.getStepRunId());
		assertEquals("attempt-1", record.getAttemptId());
	}

	private AgentExecutor executor() {
		return new AgentExecutor() {
			@Override
			public String getType() { return "fake"; }

			@Override
			public ExecutionResult execute(ExecutionContext context) {
				ExecutionResult result = new ExecutionResult();
				result.setSuccess(true);
				return result;
			}
		};
	}
}
