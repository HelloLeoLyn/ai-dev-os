package com.aidevos.orchestrator.testagent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import org.springframework.stereotype.Service;

/**
 * Testing Agent: plans, runs and records automated tests. Test commands run
 * through the TestCommandRunner abstraction (not bound to OpenClaw). Lifecycle
 * is recorded through Audit; failed tests persist a BUG_RECORD into Memory.
 * ExecutionEngine / Scheduler / Worker are not touched.
 */
@Service
public class TestAgentService {

	private static final String DEFAULT_PROJECT = "default";
	private static final int LOG_LIMIT = 4000;
	private static final String DEFAULT_UI_COMMAND = "npx playwright test";

	private static final TestAgent TESTING_AGENT = new TestAgent("testing",
		"Testing Agent", "TEST", List.of("test", "command"));

	private final Map<String, TestPlan> plans = new ConcurrentHashMap<>();
	private final TestCommandRunner commandRunner;
	private final BrowserTestExecutor browserExecutor;
	private final TaskCenterService taskCenterService;
	private final AuditService auditService;
	private final MemoryService memoryService;

	public TestAgentService(TestCommandRunner commandRunner, BrowserTestExecutor browserExecutor,
			TaskCenterService taskCenterService, AuditService auditService,
			MemoryService memoryService) {
		this.commandRunner = commandRunner;
		this.browserExecutor = browserExecutor;
		this.taskCenterService = taskCenterService;
		this.auditService = auditService;
		this.memoryService = memoryService;
	}

	public TestAgent agent() {
		return TESTING_AGENT;
	}

	public TestPlan createTest(CreateTestRequest request) {
		if (request == null || request.testType() == null) {
			throw new IllegalArgumentException("Test type is required");
		}
		if (request.taskId() != null && !request.taskId().isBlank()
				&& taskCenterService.getTask(request.taskId()).isEmpty()) {
			throw new IllegalArgumentException("Task not found: " + request.taskId());
		}
		String testId = "test-" + UUID.randomUUID();
		String taskId = blankToNull(request.taskId());
		String executionId = blankToNull(request.executionId());
		String projectId = request.projectId() == null || request.projectId().isBlank()
			? DEFAULT_PROJECT : request.projectId().trim();
		String command = request.command() == null || request.command().isBlank()
			? defaultCommand(request.testType()) : request.command().trim();
		TestPlan plan = new TestPlan(testId, taskId, request.testType(), command, projectId,
			executionId);
		plan.markQueued();
		plans.put(testId, plan);
		auditService.testEvent(EventType.TEST_CREATED, testId, taskId, executionId, null,
			TestStatus.QUEUED.name(), "Test created", Map.of(
				"testType", request.testType().name(), "command", command, "projectId", projectId));
		execute(plan);
		return plan;
	}

	public List<TestPlan> listTests() {
		List<TestPlan> result = new ArrayList<>(plans.values());
		result.sort(Comparator.comparing(TestPlan::getCreatedAt).reversed());
		return result;
	}

	public Optional<TestPlan> getTest(String testId) {
		if (testId == null || testId.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(plans.get(testId));
	}

	private void execute(TestPlan plan) {
		plan.markRunning();
		auditService.testEvent(EventType.TEST_STARTED, plan.getTestId(), plan.getTaskId(),
			plan.getExecutionId(), TestStatus.QUEUED.name(), TestStatus.RUNNING.name(),
			"Test started", Map.of("command", plan.getCommand()));

		if (TestType.UI_TEST.equals(plan.getTestType())) {
			executeBrowserTest(plan);
		}
		else {
			executeCommandTest(plan);
		}
	}

	private void executeCommandTest(TestPlan plan) {
		TestCommandResult result;
		try {
			result = commandRunner.run(plan.getCommand(), null);
		}
		catch (RuntimeException exception) {
			fail(plan, errorMessage(exception), null);
			return;
		}

		if (result.succeeded()) {
			plan.markSuccess("exit code 0", truncate(result.output()));
			auditService.testEvent(EventType.TEST_SUCCEEDED, plan.getTestId(), plan.getTaskId(),
				plan.getExecutionId(), TestStatus.RUNNING.name(), TestStatus.SUCCESS.name(),
				"Test succeeded", Map.of("command", plan.getCommand()));
		}
		else {
			fail(plan, "exit code " + result.exitCode(), result.output());
		}
	}

	private void executeBrowserTest(TestPlan plan) {
		BrowserTestResult result;
		try {
			result = browserExecutor.execute(plan.getTestId(), plan.getCommand());
		}
		catch (RuntimeException exception) {
			fail(plan, errorMessage(exception), null);
			return;
		}
		plan.setScreenshotPath(result.screenshotPath());
		if (result.succeeded()) {
			plan.markSuccess("browser test succeeded", truncate(result.output()));
			auditService.testEvent(EventType.TEST_SUCCEEDED, plan.getTestId(), plan.getTaskId(),
				plan.getExecutionId(), TestStatus.RUNNING.name(), TestStatus.SUCCESS.name(),
				"Browser test succeeded", Map.of("command", plan.getCommand()));
		}
		else {
			fail(plan, result.errorMessage() != null && !result.errorMessage().isBlank()
				? result.errorMessage() : "browser test failed", result.output());
		}
	}

	private void fail(TestPlan plan, String error, String logs) {
		plan.markFailed(error, truncate(logs));
		auditService.testEvent(EventType.TEST_FAILED, plan.getTestId(), plan.getTaskId(),
			plan.getExecutionId(), TestStatus.RUNNING.name(), TestStatus.FAILED.name(),
			"Test failed: " + error, Map.of("command", plan.getCommand()));
		saveBugRecord(plan);
	}

	private void saveBugRecord(TestPlan plan) {
		try {
			MemoryRecord record = new MemoryRecord();
			record.setProjectId(plan.getProjectId());
			record.setType(MemoryType.BUG_RECORD);
			record.setKey("bug:" + plan.getTestId());
			record.setContent(bugContent(plan));
			memoryService.create(record);
		}
		catch (RuntimeException exception) {
			// Memory must not break the test flow; the failure is already audited.
		}
	}

	private String bugContent(TestPlan plan) {
		String detail = plan.getErrorMessage() != null ? plan.getErrorMessage()
			: firstLines(plan.getLogs(), 20);
		String timestamp = plan.getCompletedAt() != null ? plan.getCompletedAt().toString() : null;
		String screenshot = plan.getScreenshotPath() != null && !plan.getScreenshotPath().isBlank()
			? plan.getScreenshotPath() : "无";
		String solution = "修复 " + plan.getTestType() + " 测试失败后，重新运行测试命令："
			+ plan.getCommand() + "，并确认通过后复跑。";
		return "错误信息: " + (detail == null || detail.isBlank() ? "未知" : detail)
			+ System.lineSeparator()
			+ "命令: " + plan.getCommand() + System.lineSeparator()
			+ "时间: " + (timestamp == null ? "未知" : timestamp) + System.lineSeparator()
			+ "截图: " + screenshot + System.lineSeparator()
			+ "解决方案: " + solution + System.lineSeparator()
			+ "项目: " + plan.getProjectId() + System.lineSeparator()
			+ "关联任务: " + (plan.getTaskId() == null ? "无" : plan.getTaskId());
	}

	private String defaultCommand(TestType type) {
		return switch (type) {
			case UNIT_TEST -> "mvn test";
			case API_TEST -> "mvn test";
			case UI_TEST -> DEFAULT_UI_COMMAND;
			case BUILD_VERIFY -> "npm run build";
		};
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= LOG_LIMIT ? value : value.substring(0, LOG_LIMIT);
	}

	private String firstLines(String value, int lines) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String[] parts = value.split("\\R");
		int count = Math.min(parts.length, lines);
		return String.join(System.lineSeparator(), java.util.Arrays.copyOf(parts, count));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String errorMessage(Exception exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
