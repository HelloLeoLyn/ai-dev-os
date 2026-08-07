package com.aidevos.orchestrator.metrics;

import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.mcpplugin.McpPluginRegistryService;
import com.aidevos.orchestrator.memory.MemoryService;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import org.springframework.stereotype.Service;

/**
 * Aggregates lightweight operational metrics from the existing repositories
 * and registries. Read-only; no execution flow is touched.
 */
@Service
public class MetricsService {

	private final AgentManager agentManager;
	private final TaskCenterService taskCenterService;
	private final JobService jobService;
	private final MemoryService memoryService;
	private final McpPluginRegistryService pluginRegistry;

	public MetricsService(AgentManager agentManager, TaskCenterService taskCenterService,
			JobService jobService, MemoryService memoryService,
			McpPluginRegistryService pluginRegistry) {
		this.agentManager = agentManager;
		this.taskCenterService = taskCenterService;
		this.jobService = jobService;
		this.memoryService = memoryService;
		this.pluginRegistry = pluginRegistry;
	}

	public MetricsSnapshot collect() {
		return new MetricsSnapshot(
			agentManager.getAllAgents().size(),
			taskCenterService.listTasks().size(),
			jobService.getAll(JobStatus.RUNNING).size(),
			jobService.getAll(JobStatus.FAILED).size(),
			jobService.getAll(JobStatus.RECOVERY_REQUIRED).size(),
			memoryService.list(null, null).size(),
			pluginRegistry.listPlugins().size());
	}
}
