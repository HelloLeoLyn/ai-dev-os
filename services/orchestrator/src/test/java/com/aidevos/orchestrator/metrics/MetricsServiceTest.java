package com.aidevos.orchestrator.metrics;

import java.util.List;

import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPlugin;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsServiceTest {

	@Test
	void shouldAggregateCountsFromExistingRepositories() {
		AgentManager agentManager = mock(AgentManager.class);
		TaskCenterService taskCenterService = mock(TaskCenterService.class);
		JobService jobService = mock(JobService.class);
		MemoryService memoryService = mock(MemoryService.class);
		McpPluginRegistryService pluginRegistry = mock(McpPluginRegistryService.class);

		when(agentManager.getAllAgents()).thenReturn(List.of(agent("planner"), agent("coder")));
		when(taskCenterService.listTasks()).thenReturn(List.of());
		when(jobService.getAll(JobStatus.RUNNING)).thenReturn(List.of(job("job-1")));
		when(jobService.getAll(JobStatus.FAILED)).thenReturn(List.of(job("job-2")));
		when(jobService.getAll(JobStatus.RECOVERY_REQUIRED)).thenReturn(List.of());
		when(memoryService.list(null, null)).thenReturn(List.of(new MemoryRecord()));
		when(pluginRegistry.listPlugins()).thenReturn(List.of(plugin()));

		MetricsService service = new MetricsService(agentManager, taskCenterService, jobService,
			memoryService, pluginRegistry);

		MetricsSnapshot snapshot = service.collect();
		assertEquals(2, snapshot.agents());
		assertEquals(0, snapshot.tasks());
		assertEquals(1, snapshot.runningJobs());
		assertEquals(1, snapshot.failedJobs());
		assertEquals(0, snapshot.recoveryJobs());
		assertEquals(1, snapshot.memoryRecords());
		assertEquals(1, snapshot.plugins());
	}

	private com.aidevos.orchestrator.model.AgentDefinition agent(String name) {
		com.aidevos.orchestrator.model.AgentDefinition definition =
			new com.aidevos.orchestrator.model.AgentDefinition();
		definition.setName(name);
		return definition;
	}

	private ExecutionJob job(String id) {
		return new ExecutionJob(id, new TaskDefinition());
	}

	private McpPlugin plugin() {
		return new McpPlugin("filesystem", "Filesystem", "filesystem", null, "read-only", true,
			List.of());
	}
}
