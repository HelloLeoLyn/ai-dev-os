package com.aidevos.orchestrator.controller;

import java.io.File;
import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.testagent.CreateTestRequest;
import com.aidevos.orchestrator.testagent.TestAgentService;
import com.aidevos.orchestrator.testagent.TestPlan;
import com.aidevos.orchestrator.testagent.TestReport;
import com.aidevos.orchestrator.testagent.TestReportGenerator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tests")
public class TestController {

	private final TestAgentService testAgentService;
	private final TestReportGenerator reportGenerator;

	public TestController(TestAgentService testAgentService, TestReportGenerator reportGenerator) {
		this.testAgentService = testAgentService;
		this.reportGenerator = reportGenerator;
	}

	@PostMapping
	public ResponseEntity<TestPlan> create(@RequestBody CreateTestRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(testAgentService.createTest(request));
	}

	@GetMapping
	public List<TestPlan> list() {
		return testAgentService.listTests();
	}

	@GetMapping("/{id}")
	public ResponseEntity<TestPlan> get(@PathVariable String id) {
		return ResponseEntity.ok(testAgentService.getTest(id)
			.orElseThrow(() -> new ResourceNotFoundException("Test", id)));
	}

	@GetMapping("/{id}/report")
	public ResponseEntity<TestReport> report(@PathVariable String id) {
		return ResponseEntity.ok(testAgentService.getTest(id)
			.map(reportGenerator::generateAndStore)
			.orElseThrow(() -> new ResourceNotFoundException("Test", id)));
	}

	@GetMapping("/{id}/screenshot")
	public ResponseEntity<Resource> screenshot(@PathVariable String id) {
		TestPlan plan = testAgentService.getTest(id)
			.orElseThrow(() -> new ResourceNotFoundException("Test", id));
		if (plan.getScreenshotPath() == null) {
			throw new ResourceNotFoundException("Test screenshot", id);
		}
		File file = new File(plan.getScreenshotPath());
		if (!file.isFile()) {
			throw new ResourceNotFoundException("Test screenshot", id);
		}
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.body(new FileSystemResource(file));
	}
}
