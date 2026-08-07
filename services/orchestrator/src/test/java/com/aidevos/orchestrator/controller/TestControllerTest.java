package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestReport;
import com.aidevos.orchestrator.testagent.TestReportGenerator;
import com.aidevos.orchestrator.testagent.TestStatus;
import com.aidevos.orchestrator.testagent.TestType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TestControllerTest {

	@TempDir
	Path tempDir;

	@Test
	void shouldCreateTestTask() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		when(service.createTest(any(CreateTestRequest.class))).thenReturn(plan("test-1"));
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(post("/api/tests")
				.contentType("application/json")
				.content("{\"testType\":\"UNIT_TEST\",\"command\":\"mvn test\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.testId").value("test-1"))
			.andExpect(jsonPath("$.testType").value("UNIT_TEST"))
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.command").value("mvn test"));
	}

	@Test
	void shouldListTests() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		when(service.listTests()).thenReturn(java.util.List.of(plan("test-1")));
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].testId").value("test-1"));
	}

	@Test
	void shouldGetTestById() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		when(service.getTest("test-1")).thenReturn(java.util.Optional.of(plan("test-1")));
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests/test-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.testId").value("test-1"));
	}

	@Test
	void shouldReturn404WhenTestMissing() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		when(service.getTest("missing")).thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests/missing"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldReturnTestReport() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		TestReportGenerator generator = mock(TestReportGenerator.class);
		TestPlan plan = plan("test-1");
		TestReport report = new TestReport("test-1", "BUILD SUCCESS", 7, 3, 1234L,
			List.of("/api/tests/test-1/screenshot", "/api/tests/test-1/report"));
		when(service.getTest("test-1")).thenReturn(java.util.Optional.of(plan));
		when(generator.generateAndStore(plan)).thenReturn(report);
		MockMvc mockMvc = standaloneSetup(new TestController(service, generator)).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests/test-1/report"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.testId").value("test-1"))
			.andExpect(jsonPath("$.summary").value("BUILD SUCCESS"))
			.andExpect(jsonPath("$.passed").value(7))
			.andExpect(jsonPath("$.failed").value(3))
			.andExpect(jsonPath("$.duration").value(1234))
			.andExpect(jsonPath("$.artifacts[0]").value("/api/tests/test-1/screenshot"));
	}

	@Test
	void shouldReturn404WhenReportForMissingTest() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		when(service.getTest("missing")).thenReturn(java.util.Optional.empty());
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests/missing/report"))
			.andExpect(status().isNotFound());
	}

	@Test
	void shouldServeScreenshot() throws Exception {
		Path screenshot = tempDir.resolve("screenshot.png");
		Files.write(screenshot, new byte[] { 1, 2, 3 });
		TestPlan plan = plan("test-1");
		plan.setScreenshotPath(screenshot.toString());
		TestAgentService service = mock(TestAgentService.class);
		when(service.getTest("test-1")).thenReturn(java.util.Optional.of(plan));
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests/test-1/screenshot"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG));
	}

	@Test
	void shouldReturn404WhenNoScreenshot() throws Exception {
		TestAgentService service = mock(TestAgentService.class);
		when(service.getTest("test-1")).thenReturn(java.util.Optional.of(plan("test-1")));
		MockMvc mockMvc = standaloneSetup(new TestController(service,
			mock(TestReportGenerator.class))).setControllerAdvice(new GlobalExceptionHandler()).build();

		mockMvc.perform(get("/api/tests/test-1/screenshot"))
			.andExpect(status().isNotFound());
	}

	private TestPlan plan(String testId) {
		TestPlan plan = new TestPlan(testId, "task-1", TestType.UNIT_TEST, "mvn test",
			"default", null);
		plan.markRunning();
		plan.markSuccess("exit code 0", "BUILD SUCCESS");
		return plan;
	}
}
