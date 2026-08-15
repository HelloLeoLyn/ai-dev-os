package com.aidevos.orchestrator.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.InMemoryExecutionRecordRepository;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;
import com.aidevos.orchestrator.taskcenter.InMemoryTaskRepository;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisInsightServiceTest {
	private final ObjectMapper mapper=new ObjectMapper();
	private InMemoryAnalysisInsightRepository insights;
	private InMemoryTaskRepository tasks;
	private InMemoryExecutionRecordRepository executions;
	private PlanRunRepository runs;
	private AnalysisInsightService service;

	@BeforeEach void setUp() {
		insights=new InMemoryAnalysisInsightRepository(); tasks=new InMemoryTaskRepository();
		executions=new InMemoryExecutionRecordRepository(); runs=mock(PlanRunRepository.class);
		service=new AnalysisInsightService(insights,tasks,runs,executions,new AnalysisPayloadValidator(),
			new AnalysisFingerprintService(mapper),mapper,AuditService.noop());
	}
	@Test void failedTaskDoesNotProject() {
		tasks.save(task(TaskStatus.FAILED));
		assertThrows(IllegalStateException.class, () -> service.project("task-1"));
		assertNull(insights.findByTaskId("task-1"));
	}
	@Test void nonAnalysisTaskDoesNotProject() {
		tasks.save(task(TaskStatus.SUCCESS)); when(runs.get("run-1")).thenReturn(run("GENERAL"));
		assertThrows(IllegalStateException.class, () -> service.project("task-1"));
	}
	@Test void validArtifactProjectsAndRetryIsIdempotent() {
		eligible(); executions.save(execution(validPayload()));
		AnalysisInsightSet first=service.project("task-1"), retried=service.retry("task-1");
		assertEquals(AnalysisEnums.Status.SUCCEEDED,first.status()); assertEquals(first.analysisId(),retried.analysisId());
		assertEquals(1,insights.findByProjectId("project-1").size());
	}
	@Test void incompleteArtifactFailsExtractionWithoutChangingSuccessfulTask() {
		eligible(); executions.save(execution("{\"schemaVersion\":\"1.0\""));
		AnalysisInsightSet result=service.project("task-1");
		assertEquals(AnalysisEnums.Status.FAILED,result.status());
		assertEquals(TaskStatus.SUCCESS,tasks.get("task-1").getStatus());
	}
	@Test void retryOnlyReprojectsExistingExecution() {
		eligible(); executions.save(execution(validPayload())); service.project("task-1");
		service.retry("task-1");
		assertEquals(1,executions.getAll().size()); verify(runs,atLeastOnce()).get("run-1");
	}
	@Test void invalidHistoricalEvidenceRetryIsStableAndDoesNotFingerprintOrRerunTask() {
		eligible(); executions.save(execution(invalidEvidencePayload()));
		AnalysisInsightSet first=service.project("task-1"), retried=service.retry("task-1");
		assertEquals(AnalysisEnums.Status.FAILED,first.status());
		assertEquals(first.analysisId(),retried.analysisId()); assertNull(retried.contentFingerprint());
		assertEquals(1,insights.findByProjectId("project-1").size()); assertEquals(1,executions.getAll().size());
		assertEquals(TaskStatus.SUCCESS,tasks.get("task-1").getStatus());
	}
	@Test void validArtifactAndSourceFileEvidenceProjectsWithFingerprint() {
		eligible(); executions.save(execution(validEvidencePayload()));
		AnalysisInsightSet result=service.project("task-1");
		assertEquals(AnalysisEnums.Status.SUCCEEDED,result.status()); assertNotNull(result.contentFingerprint());
	}
	@Test void recoveryMarksRunningAsInterruptedAndLeavesTaskSuccess() {
		eligible(); Instant now=Instant.now(); insights.save(new AnalysisInsightSet("analysis-1","task-1",
			"execution-1","project-1","workspace-1",List.of(),AnalysisEnums.ExtractorType.STRUCTURED,
			AnalysisInsightService.EXTRACTOR_VERSION,"1.0",AnalysisEnums.Status.RUNNING,null,null,null,
			List.of(),List.of(),now,now));
		service.recoverInterrupted();
		assertEquals("PROJECTION_INTERRUPTED",insights.get("analysis-1").errorCode());
		assertEquals(TaskStatus.SUCCESS,tasks.get("task-1").getStatus());
	}
	private void eligible() { tasks.save(task(TaskStatus.SUCCESS)); when(runs.get("run-1")).thenReturn(run("project-analysis")); }
	private TaskRecord task(TaskStatus status) { return TaskRecord.restore("task-1","Analysis","Analyze",
		"project-1","workspace-1",ExecutionMode.READ_ONLY,status,Instant.now(),Instant.now(),
		"approval-1","run-1",null); }
	private PlanRun run(String type) { PlanSnapshot snapshot=new PlanSnapshot(List.of(),java.util.Set.of(),
		List.of(),java.util.Set.of(),"v",Map.of("taskType",type)); Plan plan=new Plan("plan-1",1,"goal",null,
		List.of(),List.of(),snapshot,Instant.now()); return new PlanRun("run-1","approval-1","task-1",plan,List.of(),Instant.now()); }
	private ExecutionRecord execution(String content) { ExecutionArtifact artifact=new ExecutionArtifact();
		artifact.setType("analysis-result"); artifact.setName("analysis-result.json"); artifact.setMediaType("application/json"); artifact.setContent(content);
		ExecutionArtifact events=new ExecutionArtifact(); events.setType("codex-events"); events.setName("codex-events.jsonl"); events.setContent("event");
		ExecutionRecord value=new ExecutionRecord(); value.setId("execution-1"); value.setTaskId("task-1"); value.setStatus("SUCCESS");
		value.setCompletedAt(Instant.now()); value.setArtifacts(List.of(artifact,events)); return value; }
	private String invalidEvidencePayload() { return validPayload().replace("\"evidenceRefs\":[]",
		"\"evidenceRefs\":[{\"type\":\"EXECUTION_RECORD\",\"ref\":\"git diff --check\",\"label\":null,\"artifactType\":null,\"uri\":null,\"line\":null,\"contentHash\":null}]"); }
	private String validEvidencePayload() { return validPayload().replace("\"evidenceRefs\":[]",
		"\"evidenceRefs\":[{\"type\":\"SOURCE_FILE\",\"ref\":\"jjx-web/src/views/system/user/index.vue\",\"label\":null,\"artifactType\":null,\"uri\":null,\"line\":null,\"contentHash\":null},{\"type\":\"ARTIFACT\",\"ref\":\"codex-events.jsonl\",\"label\":null,\"artifactType\":\"codex-events\",\"uri\":null,\"line\":null,\"contentHash\":null}]"); }
	private String validPayload() { return """
		{"schemaVersion":"1.0","summary":"ok","findings":[{"findingId":"f1","title":"Finding","summary":"Summary","category":"QUALITY","severity":"HIGH","confidence":0.9,"scope":["src"],"evidenceRefs":[]}],"recommendations":[{"recommendationId":"r1","findingIds":["f1"],"title":"Recommendation","rationale":"Reason","priority":"HIGH","risk":"MEDIUM","benefit":"HIGH","scope":["src"],"dependencies":[],"suggestedExecutionMode":"READ_WRITE","approvalRequired":false,"evidenceRefs":[],"confidence":0.8,"recommendedNextAction":{"actionId":"a1","title":"Act","description":"Description","goal":"Goal","acceptanceCriteria":["Done"],"scope":["src"],"dependencies":[],"suggestedExecutionMode":"READ_WRITE","approvalRequired":false,"estimatedComplexity":"SMALL"}}]}
		"""; }
}
