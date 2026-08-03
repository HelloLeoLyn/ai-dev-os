package com.aidevos.orchestrator.planner.replan;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.plan.run.StepRunStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReplanRequestService {

	private final ReplanRequestRepository store;
	private final FailureClassifier classifier;
	private final Clock clock;

	@Autowired
	public ReplanRequestService(ReplanRequestRepository store, FailureClassifier classifier) {
		this(store, classifier, Clock.systemUTC());
	}

	public ReplanRequestService(ReplanRequestRepository store, FailureClassifier classifier, Clock clock) {
		this.store = store;
		this.classifier = classifier;
		this.clock = clock;
	}

	public ReplanRequest create(PlanRun run, StepRun failedStep, ExecutionJob job,
			String reason, boolean artifactMissing) {
		ExecutionResult result = job == null ? null : job.getResult();
		ReplanRequest request = new ReplanRequest(UUID.randomUUID().toString(), run.getPlanId(),
			run.getPlanVersion(), run.getId(), failedStep.getStepId(),
			classifier.classify(reason, result, artifactMissing), reason,
			completedSteps(run), summary(job, reason), artifacts(result), run.getPlan(),
			Instant.now(clock));
		store.save(request);
		return request;
	}

	public ReplanRequest findByPlanRun(String planRunId) {
		return store.findByPlanRun(planRunId);
	}

	public List<ReplanRequest> getAll() { return store.getAll(); }

	private List<String> completedSteps(PlanRun run) {
		return run.getSteps().stream()
			.filter(step -> step.getStatus() == StepRunStatus.SUCCESS)
			.map(StepRun::getStepId).toList();
	}

	private ReplanRequest.ExecutionRecordSummary summary(ExecutionJob job, String reason) {
		if (job == null) {
			return null;
		}
		return new ReplanRequest.ExecutionRecordSummary(job.getExecutionRecordId(), job.getTaskId(),
			job.getTaskSnapshot().getAgentName(), job.getStatus().name(), reason);
	}

	private List<ReplanRequest.ArtifactReference> artifacts(ExecutionResult result) {
		if (result == null || result.getArtifacts() == null) {
			return List.of();
		}
		return result.getArtifacts().stream().map(this::reference).toList();
	}

	private ReplanRequest.ArtifactReference reference(ExecutionArtifact artifact) {
		return new ReplanRequest.ArtifactReference(artifact.getType(), artifact.getName(),
			artifact.getMediaType(), artifact.getUri());
	}
}
