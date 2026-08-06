package com.aidevos.orchestrator.dashboard;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.health.ReadinessGate;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.task.TaskManager;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

	private static final int RECENT_JOB_LIMIT = 10;

	private final TaskManager taskManager;
	private final JobRepository jobStore;
	private final ExecutionRecordManager executionRecordManager;
	private final AgentManager agentManager;
	private final ReadinessGate readinessGate;

	public DashboardService(TaskManager taskManager, JobRepository jobStore,
			ExecutionRecordManager executionRecordManager, AgentManager agentManager,
			ReadinessGate readinessGate) {
		this.taskManager = taskManager;
		this.jobStore = jobStore;
		this.executionRecordManager = executionRecordManager;
		this.agentManager = agentManager;
		this.readinessGate = readinessGate;
	}

	public DashboardSummary getSummary() {
		List<TaskDefinition> tasks = taskManager.getAllTasks();
		List<ExecutionJob> jobs = jobStore.getAll();
		List<ExecutionRecord> executions = executionRecordManager.getAll();
		return new DashboardSummary(Instant.now(), taskStatistics(tasks),
			jobStatistics(jobs), executionStatistics(executions), recentJobs(jobs));
	}

	public DashboardSummaryDTO getDashboardSummary() {
		List<ExecutionJob> jobs = jobStore.getAll();
		List<ExecutionRecord> executions = executionRecordManager.getAll();
		List<AgentDefinition> agents = agentManager.getAllAgents();
		long recoveryPending = jobs.stream()
			.filter(job -> job.getStatus() == JobStatus.RECOVERY_REQUIRED)
			.count();
		return new DashboardSummaryDTO(
			new DashboardSummaryDTO.Health("UP", readinessGate.isReady()),
			new DashboardSummaryDTO.Agents(agents.size(),
				Math.toIntExact(agents.stream().filter(AgentDefinition::isEnabled).count())),
			jobStatistics(jobs),
			executionStatistics(executions),
			new DashboardSummaryDTO.Recovery(Math.toIntExact(recoveryPending)));
	}

	private TaskStatistics taskStatistics(List<TaskDefinition> tasks) {
		Map<String, Long> byStatus = new LinkedHashMap<>();
		for (TaskDefinition task : tasks) {
			String status = normalizeTaskStatus(task.getStatus());
			byStatus.merge(status, 1L, Long::sum);
		}
		return new TaskStatistics(tasks.size(), byStatus);
	}

	private String normalizeTaskStatus(String status) {
		return status == null || status.isBlank() ? "UNKNOWN" : status.trim();
	}

	private JobStatistics jobStatistics(List<ExecutionJob> jobs) {
		long queued = countJobs(jobs, JobStatus.QUEUED);
		long running = countJobs(jobs, JobStatus.RUNNING);
		long succeeded = countJobs(jobs, JobStatus.SUCCESS);
		long failed = countJobs(jobs, JobStatus.FAILED);
		return new JobStatistics(jobs.size(), queued, running, succeeded, failed,
			percentage(succeeded, succeeded + failed));
	}

	private long countJobs(List<ExecutionJob> jobs, JobStatus status) {
		return jobs.stream().filter(job -> job.getStatus() == status).count();
	}

	private ExecutionStatistics executionStatistics(List<ExecutionRecord> records) {
		long successful = records.stream().filter(record -> statusIs(record, "SUCCESS")).count();
		long failed = records.stream().filter(record -> statusIs(record, "FAILED")).count();
		long unknown = records.size() - successful - failed;
		return new ExecutionStatistics(records.size(), successful, failed, unknown,
			percentage(successful, successful + failed));
	}

	private boolean statusIs(ExecutionRecord record, String expected) {
		return record.getStatus() != null && record.getStatus().equalsIgnoreCase(expected);
	}

	private double percentage(long successful, long terminal) {
		if (terminal == 0) {
			return 0.0;
		}
		double percentage = successful * 100.0 / terminal;
		return Math.round(percentage * 100.0) / 100.0;
	}

	private List<RecentJobSummary> recentJobs(List<ExecutionJob> jobs) {
		return jobs.stream()
			.sorted(Comparator.comparing(ExecutionJob::getCreatedAt).reversed()
				.thenComparing(ExecutionJob::getId))
			.limit(RECENT_JOB_LIMIT)
			.map(this::recentJob)
			.toList();
	}

	private RecentJobSummary recentJob(ExecutionJob job) {
		return new RecentJobSummary(job.getId(), job.getTaskId(), job.getStatus(),
			job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(),
			job.getExecutionRecordId(), job.getResultSummary(), job.getErrorMessage());
	}
}
