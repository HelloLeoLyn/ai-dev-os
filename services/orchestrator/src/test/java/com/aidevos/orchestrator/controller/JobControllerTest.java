package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.common.exception.GlobalExceptionHandler;

import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobWorker;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.mockito.Mockito.mock;

class JobControllerTest {

	@Test
	void shouldSubmitAndQueryJob() throws Exception {
		TaskManager taskManager = new TaskManager();
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		taskManager.register(task);
		JobService jobService = new JobService(new JobStore(),
			new JobWorker(mock(ExecutionEngine.class), 1));
		MockMvc mockMvc = standaloneSetup(new JobController(taskManager, jobService)).setControllerAdvice(new GlobalExceptionHandler()).build();

		String response = mockMvc.perform(post("/api/tasks/task-1/jobs"))
			.andExpect(status().isAccepted())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.taskId").value("task-1"))
			.andExpect(jsonPath("$.status").value("QUEUED"))
			.andReturn().getResponse().getContentAsString();
		String jobId = response.substring(response.indexOf("\"jobId\":\"") + 9,
			response.indexOf("\"", response.indexOf("\"jobId\":\"") + 9));

		mockMvc.perform(get("/api/jobs/{id}", jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(jobId))
			.andExpect(jsonPath("$.status").value("QUEUED"));
	}

	@Test
	void shouldReturnEmptyJobList() throws Exception {
		MockMvc mockMvc = mockMvc(new JobStore());

		mockMvc.perform(get("/api/jobs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void shouldListAllJobsAndFilterByStatus() throws Exception {
		JobStore store = new JobStore();
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob queued = new ExecutionJob("job-queued", task);
		ExecutionJob failed = new ExecutionJob("job-failed", task);
		failed.markFailed(null, "failed");
		store.save(queued);
		store.save(failed);
		MockMvc mockMvc = mockMvc(store);

		mockMvc.perform(get("/api/jobs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(get("/api/jobs").param("status", "FAILED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value("job-failed"))
			.andExpect(jsonPath("$[0].status").value("FAILED"));
	}

	@Test
	void shouldReturnExecutionResultSummary() throws Exception {
		JobStore store = new JobStore();
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		ExecutionJob job = new ExecutionJob("job-1", task);
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		result.setMessage("Task executed successfully");
		result.setOutput("execution output");
		job.markSucceeded(result, "record-1");
		store.save(job);
		MockMvc mockMvc = mockMvc(store);

		mockMvc.perform(get("/api/jobs/job-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.executionRecordId").value("record-1"))
			.andExpect(jsonPath("$.resultSummary").value("Task executed successfully"))
			.andExpect(jsonPath("$.errorMessage").doesNotExist());
	}

	private MockMvc mockMvc(JobStore store) {
		JobService jobService = new JobService(store,
			new JobWorker(mock(ExecutionEngine.class), 1));
		return standaloneSetup(new JobController(new TaskManager(), jobService)).setControllerAdvice(new GlobalExceptionHandler()).build();
	}
}
