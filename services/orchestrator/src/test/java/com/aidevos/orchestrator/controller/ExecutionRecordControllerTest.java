package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionReport;
import com.aidevos.orchestrator.execution.query.ExecutionRecordQueryService;
import com.aidevos.orchestrator.model.ExecutionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ExecutionRecordControllerTest {

	private ExecutionRecordManager manager;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		manager = new ExecutionRecordManager();
		mockMvc = standaloneSetup(new ExecutionRecordController(
			new ExecutionRecordQueryService(manager))).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void shouldReturnSummaryJsonWithoutOutputOrReport() throws Exception {
		manager.save(record("record-1", "task-1", "SUCCESS"));

		mockMvc.perform(get("/api/execution-records"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value("record-1"))
			.andExpect(jsonPath("$[0].taskId").value("task-1"))
			.andExpect(jsonPath("$[0].agentName").value("coder"))
			.andExpect(jsonPath("$[0].status").value("SUCCESS"))
			.andExpect(jsonPath("$[0].message").value("message"))
			.andExpect(jsonPath("$[0].output").doesNotExist())
			.andExpect(jsonPath("$[0].report").doesNotExist());
	}

	@Test
	void shouldApplyStatusAndTaskFilters() throws Exception {
		manager.save(record("record-1", "task-1", "SUCCESS"));
		manager.save(record("record-2", "task-2", "FAILED"));

		mockMvc.perform(get("/api/execution-records")
				.param("status", "failed")
				.param("taskId", "task-2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value("record-2"));
	}

	@Test
	void shouldReturnFullDetailJson() throws Exception {
		manager.save(record("record-1", "task-1", "SUCCESS"));

		mockMvc.perform(get("/api/execution-records/record-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.output").value("execution output"))
			.andExpect(jsonPath("$.report.success").value(true))
			.andExpect(jsonPath("$.report.beforeGitStatus").value("git status"))
			.andExpect(jsonPath("$.report.afterGitDiff").value("git diff"));
	}

	@Test
	void shouldReturnNotFoundForUnknownRecord() throws Exception {
		mockMvc.perform(get("/api/execution-records/unknown"))
			.andExpect(status().isNotFound());
	}

	private ExecutionRecord record(String id, String taskId, String status) {
		ExecutionReport report = new ExecutionReport();
		report.setTaskId(taskId);
		report.setAgentName("coder");
		report.setSuccess("SUCCESS".equals(status));
		report.setBeforeGitStatus("git status");
		report.setAfterGitDiff("git diff");
		report.setOutput("execution output");
		ExecutionRecord record = new ExecutionRecord();
		record.setId(id);
		record.setTaskId(taskId);
		record.setAgentName("coder");
		record.setStatus(status);
		record.setMessage("message");
		record.setOutput("execution output");
		record.setReport(report);
		return record;
	}
}
