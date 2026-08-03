package com.aidevos.orchestrator.plan.schedule;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobSubmissionResponse;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.ArtifactReference;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.plan.run.StepRunStatus;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlanScheduler {

	private static final long POLL_MILLIS = 25;

	private final JobService jobService;
	private final StepTaskFactory taskFactory;
	private final PlanApprovalService approvalService;
	private final ReplanRequestService replanRequestService;
	private final Clock clock;
	private final PlanRunRepository runRepository;
	private final AuditService auditService;
	private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
		Thread.ofPlatform().daemon().name("plan-run-monitor").factory());

	public PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService) {
		this(jobService, taskFactory, approvalService, replanRequestService,
			new InMemoryPlanRunRepository(), Clock.systemUTC(), AuditService.noop());
	}

	@Autowired
	public PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			PlanRunRepository runRepository, AuditService auditService) {
		this(jobService, taskFactory, approvalService, replanRequestService,
			runRepository, Clock.systemUTC(), auditService);
	}

	PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			Clock clock) {
		this(jobService, taskFactory, approvalService, replanRequestService,
			new InMemoryPlanRunRepository(), clock, AuditService.noop());
	}

	PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			PlanRunRepository runRepository, Clock clock) {
		this(jobService, taskFactory, approvalService, replanRequestService, runRepository, clock,
			AuditService.noop());
	}

	PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			PlanRunRepository runRepository, Clock clock, AuditService auditService) {
		this.jobService = jobService;
		this.taskFactory = taskFactory;
		this.approvalService = approvalService;
		this.replanRequestService = replanRequestService;
		this.clock = clock;
		this.runRepository = runRepository;
		this.auditService = auditService;
	}

	@PostConstruct
	void startMonitor() {
		monitor.scheduleWithFixedDelay(this::safeReconcile, POLL_MILLIS, POLL_MILLIS,
			TimeUnit.MILLISECONDS);
	}

	public synchronized PlanRun start(String approvalId) {
		PlanApprovalRequest approval = approvalService.get(approvalId);
		if (approval == null) {
			throw new IllegalArgumentException("Plan approval not found: " + approvalId);
		}
		if (runRepository.findRunIdByApproval(approvalId) != null) {
			throw new IllegalStateException("Plan approval has already started a run");
		}
		if (approval.getStatus() != ApprovalStatus.APPROVED) {
			throw new IllegalStateException("Plan must be approved before execution");
		}
		Plan plan = approval.getPlan();
		List<StepRun> stepRuns = plan.steps().stream()
			.map(step -> new StepRun(UUID.randomUUID().toString(), step.id()))
			.toList();
		PlanRun run = new PlanRun(UUID.randomUUID().toString(), approvalId, plan, stepRuns,
			Instant.now(clock));
		register(approvalId, run);
		try {
			PlanApprovalRequest consumed = approvalService.consume(approvalId);
			if (consumed.getStatus() != ApprovalStatus.CONSUMED) {
				throw new IllegalStateException("Plan approval was not consumed");
			}
		}
		catch (RuntimeException exception) {
			unregister(approvalId, run.getId());
			throw exception;
		}
		auditService.planRunCreated(run);
		PlanRunStatus before = run.getStatus();
		run.markRunning(Instant.now(clock));
		advance(run);
		runRepository.save(run);
		auditService.planRunTransition(run, before.name(), run.getStatus().name());
		return run;
	}

	private void register(String approvalId, PlanRun run) {
		runRepository.create(approvalId, run);
	}

	private void unregister(String approvalId, String runId) {
		runRepository.remove(approvalId, runId);
	}

	public PlanRun get(String runId) {
		return runRepository.get(runId);
	}

	public List<PlanRun> getAll() {
		return runRepository.getAll();
	}

	public void reconcile() {
		for (PlanRun run : new ArrayList<>(runRepository.getAll())) {
			reconcile(run);
		}
	}

	private void safeReconcile() {
		try {
			reconcile();
		}
		catch (RuntimeException ignored) {
			// An individual run is transitioned to FAILED by reconcile whenever possible.
		}
	}

	private void reconcile(PlanRun run) {
		synchronized (run) {
			PlanRunStatus beforeStatus = run.getStatus();
			try {
			if (terminal(run.getStatus())) {
				return;
			}
			StepRun active = activeStep(run);
			if (active == null) {
				advance(run);
				return;
			}
			StepAttempt attempt = active.getCurrentAttempt();
			ExecutionJob job = attempt == null ? null : jobService.get(attempt.getJobId());
			if (job == null) {
				fail(run, active, attempt, "Submitted job not found", null, null, false);
				return;
			}
			if (job.getStatus() == JobStatus.WAITING_APPROVAL) {
				attempt.markWaitingApproval();
				active.markWaitingApproval();
				run.markWaitingApproval();
				return;
			}
			if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RUNNING) {
				attempt.markRunning();
				active.markRunning();
				run.markRunning(Instant.now(clock));
				return;
			}
			if (job.getStatus() == JobStatus.FAILED) {
				fail(run, active, attempt, job.getErrorMessage(), job.getExecutionRecordId(), job,
					false);
				return;
			}
			if (!resultSatisfies(run, active, job.getResult())) {
				fail(run, active, attempt, "Expected artifact requirements were not satisfied",
					job.getExecutionRecordId(), job, true);
				return;
			}
			Instant now = Instant.now(clock);
			attempt.markSuccess(job.getExecutionRecordId(), now);
			active.markSuccess(now);
			advance(run);
			}
			finally {
				runRepository.save(run);
				auditService.planRunTransition(run, beforeStatus.name(), run.getStatus().name());
			}
		}
	}

	private void advance(PlanRun run) {
		if (run.getSteps().stream().allMatch(step -> step.getStatus() == StepRunStatus.SUCCESS)) {
			run.markSuccess(Instant.now(clock));
			return;
		}
		StepRun next = run.getSteps().stream()
			.filter(step -> step.getStatus() == StepRunStatus.PENDING)
			.filter(step -> dependenciesSucceeded(run, step.getStepId()))
			.findFirst().orElse(null);
		if (next == null) {
			run.markFailed("No executable step remains", Instant.now(clock));
			return;
		}
		PlanStep definition = step(run.getPlan(), next.getStepId());
		StepAttempt attempt = next.startAttempt(UUID.randomUUID().toString(), Instant.now(clock));
		try {
			TaskDefinition task = taskFactory.create(run, definition, next, attempt,
				resolveInputs(run, definition));
			JobSubmissionResponse submission = jobService.submit(task);
			attempt.bindJob(submission.jobId());
		}
		catch (RuntimeException exception) {
			fail(run, next, attempt, errorMessage(exception), null, null, false);
		}
	}

	private Map<String, Object> resolveInputs(PlanRun run, PlanStep step) {
		Map<String, Object> inputs = new java.util.LinkedHashMap<>();
		for (ArtifactReference reference : step.inputArtifacts()) {
			StepRun sourceRun = stepRun(run, reference.fromStepId());
			StepAttempt sourceAttempt = sourceRun.getCurrentAttempt();
			ExecutionJob sourceJob = sourceAttempt == null ? null
				: jobService.get(sourceAttempt.getJobId());
			ExecutionArtifact artifact = sourceJob == null || sourceJob.getResult() == null
				? null : sourceJob.getResult().getArtifacts().stream()
					.filter(item -> reference.artifactType().equals(item.getType()))
					.filter(item -> reference.artifactName() == null
						|| reference.artifactName().isBlank()
						|| reference.artifactName().equals(item.getName()))
					.findFirst().orElse(null);
			if (artifact == null) {
				if (reference.required()) {
					throw new IllegalStateException("Required input artifact is unavailable: "
						+ reference.inputKey());
				}
				continue;
			}
			Map<String, Object> value = new java.util.LinkedHashMap<>();
			value.put("fromStepId", reference.fromStepId());
			value.put("executionRecordId", sourceJob.getExecutionRecordId());
			value.put("type", artifact.getType());
			value.put("name", artifact.getName());
			value.put("mediaType", artifact.getMediaType());
			value.put("uri", artifact.getUri());
			value.put("content", artifact.getContent());
			inputs.put(reference.inputKey(), value);
		}
		return inputs;
	}

	private boolean dependenciesSucceeded(PlanRun run, String stepId) {
		return run.getPlan().dependencies().stream()
			.filter(dependency -> dependency.toStepId().equals(stepId))
			.map(Dependency::fromStepId)
			.allMatch(source -> stepRun(run, source).getStatus() == StepRunStatus.SUCCESS);
	}

	private boolean resultSatisfies(PlanRun run, StepRun stepRun, ExecutionResult result) {
		if (result == null || !result.isSuccess()) {
			return false;
		}
		PlanStep step = step(run.getPlan(), stepRun.getStepId());
		for (ExpectedArtifact expected : step.expectedArtifacts()) {
			long count = result.getArtifacts().stream()
				.filter(artifact -> matches(artifact, expected))
				.count();
			if (count < expected.minimumCount()) {
				return false;
			}
		}
		return true;
	}

	private boolean matches(ExecutionArtifact artifact, ExpectedArtifact expected) {
		return same(expected.type(), artifact.getType())
			&& optionalSame(expected.name(), artifact.getName())
			&& optionalSame(expected.mediaType(), artifact.getMediaType());
	}

	private boolean same(String expected, String actual) {
		return expected != null && expected.equals(actual);
	}

	private boolean optionalSame(String expected, String actual) {
		return expected == null || expected.isBlank() || expected.equals(actual);
	}

	private void fail(PlanRun run, StepRun step, StepAttempt attempt, String error,
			String recordId, ExecutionJob job, boolean artifactMissing) {
		String message = error == null || error.isBlank() ? "Step execution failed" : error;
		Instant now = Instant.now(clock);
		if (attempt != null) {
			attempt.markFailed(recordId, message, now);
		}
		step.markFailed(message, now);
		PlanStep definition = step(run.getPlan(), step.getStepId());
		if (definition.failurePolicy() == FailurePolicy.REQUEST_REPLAN) {
			replanRequestService.create(run, step, job, message, artifactMissing);
			run.markReplanRequired(message, now);
		}
		else {
			run.markFailed(message, now);
		}
	}

	private StepRun activeStep(PlanRun run) {
		return run.getSteps().stream()
			.filter(step -> step.getStatus() == StepRunStatus.RUNNING
				|| step.getStatus() == StepRunStatus.WAITING_APPROVAL)
			.findFirst().orElse(null);
	}

	private StepRun stepRun(PlanRun run, String stepId) {
		return run.getSteps().stream().filter(step -> step.getStepId().equals(stepId))
			.findFirst().orElseThrow();
	}

	private PlanStep step(Plan plan, String stepId) {
		return plan.steps().stream().filter(step -> step.id().equals(stepId))
			.findFirst().orElseThrow();
	}

	private boolean terminal(PlanRunStatus status) {
		return status == PlanRunStatus.SUCCESS || status == PlanRunStatus.FAILED
			|| status == PlanRunStatus.REPLAN_REQUIRED;
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	@PreDestroy
	void stopMonitor() {
		monitor.shutdownNow();
	}
}
