package com.aidevos.orchestrator.recovery;

import java.util.function.Function;

import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.springframework.stereotype.Component;

/**
 * RECOVERY-EXECUTION-RESOLVER-CLOSEOUT：
 * 从现有持久化状态（ExecutionJob.taskSnapshot = TaskDefinition 全量快照）安全重建
 * RETRY_EXECUTION 所需的 TaskDefinition。不新增第二套 TaskDefinition 持久化。
 *
 * 完整性校验（fail-closed，不猜字段）：
 * - 必须有该 task 的 job 快照（无 → 人工）
 * - snapshot.id 必须等于 taskId（快照必须属于该 task）
 * - agentName 必须非空（不猜 agent）
 * - metadata 必须存在（executionMode/requestedModelId/originalTaskId 等上下文）
 * 任一项不满足 → null → HUMAN_INTERVENTION。
 */
@Component
public class ExecutionTaskDefinitionResolver implements Function<String, TaskDefinition> {

	private final JobRepository jobRepository;
	private final TaskCenterService taskCenterService;

	public ExecutionTaskDefinitionResolver(JobRepository jobRepository,
			TaskCenterService taskCenterService) {
		this.jobRepository = jobRepository;
		this.taskCenterService = taskCenterService;
	}

	@Override
	public TaskDefinition apply(String taskId) {
		if (taskId == null || taskId.isBlank()) {
			return null;
		}
		ExecutionJob job = latestJobForTask(taskId);
		if (job == null) {
			return null;
		}
		TaskDefinition snapshot = job.getTaskSnapshot();
		if (!isTrustworthy(taskId, snapshot)) {
			return null;
		}
		// task 必须仍存在（交叉校验，避免对已删除任务重建）
		if (taskCenterService != null) {
			TaskRecord task = taskCenterService.getTask(taskId).orElse(null);
			if (task == null) {
				return null;
			}
		}
		return snapshot;
	}

	private ExecutionJob latestJobForTask(String taskId) {
		ExecutionJob latest = null;
		for (ExecutionJob job : jobRepository.getAll()) {
			if (taskId.equals(job.getTaskId())) {
				if (latest == null || (job.getCreatedAt() != null
						&& latest.getCreatedAt() != null
						&& job.getCreatedAt().isAfter(latest.getCreatedAt()))) {
					latest = job;
				}
			}
		}
		return latest;
	}

	private boolean isTrustworthy(String taskId, TaskDefinition definition) {
		if (definition == null) {
			return false;
		}
		if (definition.getId() == null || !taskId.equals(definition.getId())) {
			return false;
		}
		if (definition.getAgentName() == null || definition.getAgentName().isBlank()) {
			return false;
		}
		if (definition.getMetadata() == null) {
			return false;
		}
		return true;
	}
}
