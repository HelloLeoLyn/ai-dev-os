package com.aidevos.orchestrator.testagent;

import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventRecord;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAgentServiceTest {

	private FakeRunner runner;
	private FakeBrowserExecutor browserExecutor;
	private TaskCenterService taskCenterService;
	private AuditService auditService;
	private MemoryService memoryService;
	private TestAgentService service;

	@BeforeEach
	void setUp() {
		runner = new FakeRunner();
		browserExecutor = new FakeBrowserExecutor();
		taskCenterService = mock(TaskCenterService.class);
		auditService = new AuditService(new InMemoryAuditRepository());
		memoryService = new MemoryService(new InMemoryMemoryRepository());
		service = new TestAgentService(runner, browserExecutor, taskCenterService, auditService,
			memoryService);
	}

	@Test
	void shouldCreateAndRunTestForTask() {
		when(taskCenterService.getTask("task-1")).thenReturn(
			Optional.of(new TaskRecord("task-1", "Login flow", "Implement login")));
		runner.result = new TestCommandResult(0, "BUILD SUCCESS", "");

		TestPlan plan = service.createTest(
			new CreateTestRequest("task-1", TestType.UNIT_TEST, null, null, null));

		assertEquals(TestStatus.SUCCESS, plan.getStatus());
		assertEquals("task-1", plan.getTaskId());
		assertEquals("mvn test", plan.getCommand());
		assertEquals("exit code 0", plan.getResult());
		assertEquals("default", plan.getProjectId());
		assertNotNull(plan.getCompletedAt());
		assertEvent(plan.getTestId(), EventType.TEST_SUCCEEDED);
	}

	@Test
	void shouldGenerateDefaultCommandPerTestType() {
		runner.result = new TestCommandResult(0, "ok", "");

		TestPlan unit = service.createTest(
			new CreateTestRequest(null, TestType.UNIT_TEST, null, null, null));
		TestPlan build = service.createTest(
			new CreateTestRequest(null, TestType.BUILD_VERIFY, null, null, null));
		TestPlan ui = service.createTest(
			new CreateTestRequest(null, TestType.UI_TEST, null, null, null));

		assertEquals("mvn test", unit.getCommand());
		assertEquals("npm run build", build.getCommand());
		assertEquals("npx playwright test", ui.getCommand());
	}

	@Test
	void shouldUseCustomCommand() {
		runner.result = new TestCommandResult(0, "ok", "");

		TestPlan plan = service.createTest(
			new CreateTestRequest(null, TestType.API_TEST, "mvn test -Dtest=ApiTest", null, null));

		assertEquals("mvn test -Dtest=ApiTest", plan.getCommand());
	}

	@Test
	void shouldMarkFailedAndSaveBugRecordOnNonZeroExit() {
		runner.result = new TestCommandResult(1, "FAILED: 2 tests failed", "stacktrace");

		TestPlan plan = service.createTest(
			new CreateTestRequest(null, TestType.UNIT_TEST, null, null, "project-a"));

		assertEquals(TestStatus.FAILED, plan.getStatus());
		assertEquals("exit code 1", plan.getErrorMessage());
		assertTrue(plan.getLogs().contains("FAILED: 2 tests failed"));
		assertEvent(plan.getTestId(), EventType.TEST_FAILED);

		List<MemoryRecord> bugs = memoryService.list("project-a", MemoryType.BUG_RECORD);
		assertEquals(1, bugs.size());
		MemoryRecord bug = bugs.getFirst();
		assertEquals("bug:" + plan.getTestId(), bug.getKey());
		assertTrue(bug.getContent().contains("错误信息"));
		assertTrue(bug.getContent().contains("解决方案"));
		assertTrue(bug.getContent().contains("项目: project-a"));
	}

	@Test
	void shouldMarkFailedWhenRunnerThrows() {
		runner.failure = new IllegalStateException("command not found");

		TestPlan plan = service.createTest(
			new CreateTestRequest(null, TestType.BUILD_VERIFY, null, null, null));

		assertEquals(TestStatus.FAILED, plan.getStatus());
		assertEquals("command not found", plan.getErrorMessage());
		assertEvent(plan.getTestId(), EventType.TEST_FAILED);
		assertEquals(1, memoryService.list(null, MemoryType.BUG_RECORD).size());
	}

	@Test
	void shouldRejectUnknownTask() {
		when(taskCenterService.getTask("missing")).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class, () -> service.createTest(
			new CreateTestRequest("missing", TestType.UNIT_TEST, null, null, null)));
	}

	@Test
	void shouldRejectMissingTestType() {
		assertThrows(IllegalArgumentException.class,
			() -> service.createTest(new CreateTestRequest(null, null, null, null, null)));
	}

	@Test
	void shouldListAndGetTests() {
		runner.result = new TestCommandResult(0, "ok", "");
		TestPlan created = service.createTest(
			new CreateTestRequest(null, TestType.UNIT_TEST, null, null, null));

		assertEquals(1, service.listTests().size());
		assertEquals(Optional.of(created.getTestId()),
			service.getTest(created.getTestId()).map(TestPlan::getTestId));
		assertTrue(service.getTest("missing").isEmpty());
	}

	@Test
	void shouldRunUiTestThroughBrowserExecutorAndStoreScreenshot() {
		browserExecutor.result = BrowserTestResult.success("3 passed", "/tmp/shot.png");

		TestPlan plan = service.createTest(
			new CreateTestRequest(null, TestType.UI_TEST, null, null, null));

		assertEquals(TestStatus.SUCCESS, plan.getStatus());
		assertEquals("npx playwright test", plan.getCommand());
		assertEquals("/tmp/shot.png", plan.getScreenshotPath());
		assertEquals("browser test succeeded", plan.getResult());
		assertEvent(plan.getTestId(), EventType.TEST_SUCCEEDED);
	}

	@Test
	void shouldSaveBugRecordWithErrorCommandTimestampAndScreenshotOnBrowserFailure() {
		browserExecutor.result = BrowserTestResult.failure("page crashed", "exit code 1",
			"/tmp/shot.png");

		TestPlan plan = service.createTest(
			new CreateTestRequest(null, TestType.UI_TEST, null, null, "project-b"));

		assertEquals(TestStatus.FAILED, plan.getStatus());
		assertEquals("exit code 1", plan.getErrorMessage());
		assertEquals("/tmp/shot.png", plan.getScreenshotPath());
		assertEvent(plan.getTestId(), EventType.TEST_FAILED);

		MemoryRecord bug = memoryService.list("project-b", MemoryType.BUG_RECORD).getFirst();
		assertTrue(bug.getContent().contains("命令: npx playwright test"));
		assertTrue(bug.getContent().contains("时间:"));
		assertTrue(bug.getContent().contains("截图: /tmp/shot.png"));
	}

	@Test
	void shouldMarkFailedWhenBrowserExecutorThrows() {
		browserExecutor.failure = new IllegalStateException("browser unavailable");

		TestPlan plan = service.createTest(
			new CreateTestRequest(null, TestType.UI_TEST, null, null, null));

		assertEquals(TestStatus.FAILED, plan.getStatus());
		assertEquals("browser unavailable", plan.getErrorMessage());
		assertEvent(plan.getTestId(), EventType.TEST_FAILED);
		assertEquals(1, memoryService.list(null, MemoryType.BUG_RECORD).size());
	}

	@Test
	void shouldExposeTestingAgent() {
		TestAgent agent = service.agent();

		assertEquals("testing", agent.getAgentId());
		assertEquals("TEST", agent.getType());
		assertTrue(agent.getCapabilities().contains("test"));
	}

	private void assertEvent(String testId, EventType type) {
		List<EventRecord> events = auditService.query(EventQuery.all());
		assertTrue(events.stream().anyMatch(event -> type == event.type()
			&& testId.equals(event.aggregateId())), "missing audit event " + type);
	}

	private static final class FakeRunner implements TestCommandRunner {

		private TestCommandResult result = new TestCommandResult(0, "", "");
		private RuntimeException failure;

		@Override
		public TestCommandResult run(String command, String workdir) {
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	private static final class FakeBrowserExecutor implements BrowserTestExecutor {

		private BrowserTestResult result = BrowserTestResult.success("ok", null);
		private RuntimeException failure;

		@Override
		public BrowserTestResult execute(String testId, String command) {
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}
}
