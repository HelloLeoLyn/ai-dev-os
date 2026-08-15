package com.aidevos.orchestrator.analysis;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import org.springframework.stereotype.Component;

@Component
public class AnalysisPayloadValidator {
	public void validate(List<Finding> findings, List<Recommendation> recommendations,
			ExecutionRecord source) {
		if (findings == null || recommendations == null) fail("findings and recommendations are required");
		Set<String> findingIds = new HashSet<>();
		for (Finding finding : findings) {
			required(finding.findingId(), "findingId"); required(finding.title(), "finding.title");
			required(finding.summary(), "finding.summary"); required(finding.category(), "finding.category");
			if (finding.severity() == null) fail("finding.severity is required");
			confidence(finding.confidence()); evidence(finding.evidenceRefs(), source);
			if (!findingIds.add(finding.findingId())) fail("duplicate findingId");
		}
		for (Recommendation recommendation : recommendations) {
			required(recommendation.recommendationId(), "recommendationId");
			required(recommendation.title(), "recommendation.title");
			required(recommendation.rationale(), "recommendation.rationale");
			if (recommendation.priority() == null || recommendation.risk() == null
					|| recommendation.benefit() == null) fail("recommendation levels are required");
			if (recommendation.suggestedExecutionMode() == null) fail("execution mode is required");
			confidence(recommendation.confidence()); evidence(recommendation.evidenceRefs(), source);
			if (!findingIds.containsAll(recommendation.findingIds())) fail("recommendation references unknown finding");
			RecommendedNextAction action = recommendation.recommendedNextAction();
			if (action == null) fail("recommendedNextAction is required");
			required(action.actionId(), "actionId"); required(action.title(), "action.title");
			required(action.description(), "action.description"); required(action.goal(), "action.goal");
			if (action.acceptanceCriteria().isEmpty()) fail("action acceptanceCriteria is required");
			if (action.suggestedExecutionMode() == null || action.estimatedComplexity() == null)
				fail("action execution mode and complexity are required");
			if (recommendation.suggestedExecutionMode() == ExecutionMode.READ_WRITE
					&& !recommendation.approvalRequired()) fail("READ_WRITE must require approval");
		}
	}
	private void evidence(List<EvidenceRef> refs, ExecutionRecord source) {
		for (EvidenceRef ref : refs) {
			if (ref == null || ref.type() == null) fail("evidence type is required");
			required(ref.ref(), "evidence.ref");
			switch (ref.type()) {
				case EXECUTION_RECORD -> {
					if (!source.getId().equals(ref.ref()))
						fail("execution evidence ref does not match source execution record");
				}
				case ARTIFACT -> {
					boolean found = source.getArtifacts().stream().anyMatch(a -> artifactMatches(a, ref));
					if (!found) fail("artifact evidence ref is not available from source execution");
				}
				case SOURCE_FILE -> {
					Path path = Path.of(ref.ref()).normalize();
					if (path.isAbsolute() || path.startsWith(".."))
						fail("source file evidence escapes workspace boundary");
				}
				case TIMELINE_EVENT, MEMORY, URL -> fail("unsupported evidence authority in V0.1");
			}
		}
	}
	private boolean artifactMatches(ExecutionArtifact artifact, EvidenceRef ref) {
		return ref.ref().equals(artifact.getName()) || ref.ref().equals(artifact.getUri());
	}
	private void confidence(double value) { if (value < 0 || value > 1) fail("confidence must be between 0 and 1"); }
	private void required(String value, String field) { if (value == null || value.isBlank()) fail(field + " is required"); }
	private void fail(String message) { throw new IllegalArgumentException(message); }
}
