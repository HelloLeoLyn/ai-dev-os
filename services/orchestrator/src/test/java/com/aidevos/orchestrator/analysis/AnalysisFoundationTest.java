package com.aidevos.orchestrator.analysis;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums.EstimatedComplexity;
import com.aidevos.orchestrator.analysis.AnalysisEnums.EvidenceType;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Level;
import com.aidevos.orchestrator.analysis.AnalysisEnums.Status;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisFoundationTest {
	private final AnalysisPayloadValidator validator = new AnalysisPayloadValidator();
	private final ExecutionRecord execution = execution();

	@Test void structuredPayloadMapsToInsightDomain() throws Exception {
		Finding value = new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(finding()), Finding.class);
		assertEquals("finding-1", value.findingId()); assertEquals(Level.HIGH, value.severity());
	}
	@Test void findingRequiredFieldIsRejected() {
		Finding invalid = new Finding("", "title", "summary", "quality", Level.HIGH, .9, List.of(), List.of());
		assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(invalid), List.of(), execution));
	}
	@Test void recommendationRequiredFieldIsRejected() {
		Recommendation invalid = recommendation(null, ExecutionMode.READ_ONLY, true);
		assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(finding()), List.of(invalid), execution));
	}
	@Test void invalidSeverityIsRejectedByStructuredMapping() {
		assertThrows(Exception.class, () -> new ObjectMapper().readValue("{\"findingId\":\"f\",\"title\":\"t\",\"summary\":\"s\",\"category\":\"c\",\"severity\":\"URGENT\",\"confidence\":1,\"scope\":[],\"evidenceRefs\":[]}", Finding.class));
	}
	@Test void invalidPriorityRiskAndBenefitAreRejectedByStructuredMapping() {
		String json = new ObjectMapper().writeValueAsString(recommendation("r", ExecutionMode.READ_ONLY, true));
		assertThrows(Exception.class, () -> new ObjectMapper().readValue(json.replace("\"HIGH\"", "\"URGENT\""), Recommendation.class));
	}
	@Test void invalidExecutionModeIsRejectedByStructuredMapping() {
		String json = new ObjectMapper().writeValueAsString(recommendation("r", ExecutionMode.READ_ONLY, true));
		assertThrows(Exception.class, () -> new ObjectMapper().readValue(json.replace("READ_ONLY", "AUTOMATIC"), Recommendation.class));
	}
	@Test void readWriteForcesApprovalOnRecommendationAndAction() {
		Recommendation value = recommendation("r", ExecutionMode.READ_WRITE, false);
		assertTrue(value.approvalRequired()); assertTrue(value.recommendedNextAction().approvalRequired());
	}
	@Test void fingerprintIsStableAcrossObjectPropertyOrder() throws Exception {
		AnalysisFingerprintService service = new AnalysisFingerprintService(new ObjectMapper());
		assertEquals(service.fingerprint("t","e","v",new ObjectMapper().readTree("{\"b\":2,\"a\":1}")),
			service.fingerprint("t","e","v",new ObjectMapper().readTree("{\"a\":1,\"b\":2}")));
	}
	@Test void evidenceCrossingExecutionBoundaryIsRejected() {
		Finding invalid = new Finding("f", "t", "s", "c", Level.HIGH, .8, List.of(),
			List.of(new EvidenceRef(EvidenceType.EXECUTION_RECORD,"other",null,null,null,null,null)));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(List.of(invalid), List.of(), execution));
	}
	@Test void duplicateProjectionKeyIsIdempotentInMemory() {
		InMemoryAnalysisInsightRepository repository=new InMemoryAnalysisInsightRepository();
		AnalysisInsightSet first=insight("a1", Status.SUCCEEDED), second=insight("a2", Status.FAILED);
		assertEquals("a1", repository.save(first).analysisId());
		assertEquals("a1", repository.save(second).analysisId()); assertEquals(1, repository.findByProjectId("p").size());
	}
	@Test void interruptedRunningCanBePersistedAsRetryableFailure() {
		InMemoryAnalysisInsightRepository repository=new InMemoryAnalysisInsightRepository();
		repository.save(insight("a", Status.RUNNING));
		AnalysisInsightSet recovered=repository.save(repository.get("a").withStatus(Status.FAILED,
			"PROJECTION_INTERRUPTED","retry",Instant.now()));
		assertEquals(Status.FAILED,recovered.status()); assertEquals("PROJECTION_INTERRUPTED",recovered.errorCode());
	}

	private Finding finding() { return new Finding("finding-1","Finding","Summary","QUALITY",Level.HIGH,.9,List.of("src"),List.of()); }
	private Recommendation recommendation(String id, ExecutionMode mode, boolean approval) {
		return new Recommendation(id,List.of("finding-1"),"Recommendation","Because",Level.HIGH,
			Level.MEDIUM,Level.HIGH,List.of("src"),List.of(),mode,approval,List.of(),.8,
			new RecommendedNextAction("action-1","Act","Description","Goal",List.of("Done"),
				List.of("src"),List.of(),mode,approval,EstimatedComplexity.SMALL));
	}
	private ExecutionRecord execution() { ExecutionRecord value=new ExecutionRecord(); value.setId("execution-1");
		ExecutionArtifact artifact=new ExecutionArtifact(); artifact.setName("analysis-result.json"); value.setArtifacts(List.of(artifact)); return value; }
	private AnalysisInsightSet insight(String id, Status status) { Instant now=Instant.now(); return new AnalysisInsightSet(id,"t","e","p","w",List.of(),
		AnalysisEnums.ExtractorType.STRUCTURED,"v","1",status,null,null,null,List.of(),List.of(),now,now); }
}
