package com.aidevos.orchestrator.ci;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default CI provider (aidevos.ci.provider=mock) used when no real CI host is
 * wired in. It never talks to a real API. Semantics are deterministically
 * completable: trigger records a deterministic pipeline id in RUNNING, the
 * first poll after trigger still reports RUNNING and the next poll reports
 * SUCCESS, preserving the TRIGGER -> RUNNING -> CHECK -> SUCCESS lifecycle
 * instead of faking SUCCESS right after trigger. Polls are idempotent: once
 * SUCCESS, later polls stay SUCCESS. The test hook setStatus still wins (for
 * example to simulate FAILED / CANCELLED).
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.ci", name = "provider", havingValue = "mock",
	matchIfMissing = true)
public class MockCiProvider implements CiProvider {

	private static final String BASE_URL = "https://mock.dev/ci/";

	private final Map<String, CiStatus> statuses = new ConcurrentHashMap<>();
	private final Set<String> polled = ConcurrentHashMap.newKeySet();

	@Override
	public CiTriggerResult trigger(CiTriggerRequest request) {
		String pipelineId = "pipeline-" + request.pullRequestId();
		statuses.put(pipelineId, CiStatus.RUNNING);
		polled.remove(pipelineId);
		return new CiTriggerResult(pipelineId, BASE_URL + pipelineId);
	}

	@Override
	public CiRunResult getStatus(String pipelineId) {
		CiStatus status = statuses.getOrDefault(pipelineId, CiStatus.RUNNING);
		if (status == CiStatus.RUNNING && !polled.add(pipelineId)) {
			statuses.put(pipelineId, CiStatus.SUCCESS);
			return new CiRunResult(CiStatus.SUCCESS, BASE_URL + pipelineId);
		}
		return new CiRunResult(status, BASE_URL + pipelineId);
	}

	@Override
	public CiReport getReport(String pipelineId) {
		return new CiReport(BASE_URL + pipelineId, "Mock CI run " + pipelineId);
	}

	/** Test hook: simulate a finished pipeline with the given status. */
	public void setStatus(String pipelineId, CiStatus status) {
		statuses.put(pipelineId, status);
	}
}
