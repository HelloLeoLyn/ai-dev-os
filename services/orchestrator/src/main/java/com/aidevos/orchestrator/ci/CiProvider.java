package com.aidevos.orchestrator.ci;

/**
 * CI provider abstraction backed by a git host (GitHub Actions, GitLab CI or
 * a local mock). Implementations map provider responses into CiStatus. This
 * phase only observes status; it never triggers repairs or modifies code.
 */
public interface CiProvider {

	CiTriggerResult trigger(CiTriggerRequest request);

	CiRunResult getStatus(String pipelineId);

	CiReport getReport(String pipelineId);
}
