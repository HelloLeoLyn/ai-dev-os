package com.aidevos.orchestrator.orchestration;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestStatus;
import com.aidevos.orchestrator.testagent.TestType;
import org.springframework.stereotype.Component;

/**
 * TEST_AGENT node: runs the unit test suite. An ANALYZE node reproduces the
 * failure and completes with the failure detail; a VERIFY node completes
 * only when the tests pass and fails otherwise.
 */
@Component
public class TestAgentExecutor implements AgentExecutor {

	private final TestAgentService testAgentService;

	public TestAgentExecutor(TestAgentService testAgentService) {
		this.testAgentService = testAgentService;
	}

	@Override
	public AgentType type() {
		return AgentType.TEST_AGENT;
	}

	@Override
	public AgentExecutionResult execute(AgentExecutionContext context) {
		String taskId = context.getTaskId();
		if (taskId == null || taskId.isBlank()) {
			return failure(context, "Task is required for testing");
		}
		try {
			TestPlan plan = testAgentService.createTest(new CreateTestRequest(taskId,
				TestType.UNIT_TEST, null, null, null));
			if (TestStatus.SUCCESS.equals(plan.getStatus())) {
				return success(context, "Tests passed: " + plan.getTestId());
			}
			String error = plan.getErrorMessage() == null || plan.getErrorMessage().isBlank()
				? plan.getStatus().name() : plan.getErrorMessage();
			if (isAnalyzeNode(context.getNodeId())) {
				return success(context, "Test failed: " + error);
			}
			return failure(context, "Tests failed: " + error);
		}
		catch (RuntimeException exception) {
			return failure(context, errorMessage(exception));
		}
	}

	private boolean isAnalyzeNode(String nodeId) {
		return nodeId != null && nodeId.toUpperCase().contains("ANALYZE");
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	private AgentExecutionResult success(AgentExecutionContext context, String output) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED, output, null);
	}

	private AgentExecutionResult failure(AgentExecutionContext context, String error) {
		return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED, null, error);
	}
}
