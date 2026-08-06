package com.aidevos.orchestrator.controller;

import java.io.File;
import java.util.List;
import java.util.Optional;

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
		return testAgentService.getTest(id)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/report")
	public ResponseEntity<TestReport> report(@PathVariable String id) {
		return testAgentService.getTest(id)
			.map(reportGenerator::generateAndStore)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/screenshot")
	public ResponseEntity<Resource> screenshot(@PathVariable String id) {
		Optional<TestPlan> plan = testAgentService.getTest(id);
		if (plan.isEmpty() || plan.get().getScreenshotPath() == null) {
			return ResponseEntity.notFound().build();
		}
		File file = new File(plan.get().getScreenshotPath());
		if (!file.isFile()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.body(new FileSystemResource(file));
	}
}
