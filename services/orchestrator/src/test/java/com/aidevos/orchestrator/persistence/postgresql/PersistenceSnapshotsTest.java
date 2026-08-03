package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.approval.*;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.*;
import com.aidevos.orchestrator.plan.run.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceSnapshotsTest {
	private final ObjectMapper mapper=new ObjectMapper();
	@Test void roundTripsStatefulSnapshots() throws Exception {
		TaskDefinition task=new TaskDefinition(); task.setId("task-1");
		ExecutionJob job=new ExecutionJob("job-1",task); job.markRunning(); job.markFailed(null,"boom");
		var jobSnapshot=roundTrip(PersistenceSnapshots.Job.of(job),PersistenceSnapshots.Job.class).value();
		assertEquals(JobStatus.FAILED,jobSnapshot.getStatus()); assertEquals("boom",jobSnapshot.getErrorMessage());

		CodingApprovalRequest approval=new CodingApprovalRequest("approval-1","task-1","job-1","/work","workspace-write","test");
		approval.approve(); approval.consume();
		var approvalSnapshot=roundTrip(PersistenceSnapshots.CodingApproval.of(approval),PersistenceSnapshots.CodingApproval.class).value();
		assertEquals(ApprovalStatus.CONSUMED,approvalSnapshot.getStatus()); assertEquals(approval.getCreatedAt(),approvalSnapshot.getCreatedAt());

		Plan plan=new Plan("plan-1",1,"goal",PlanStatus.APPROVED,List.of(),List.of(),null,Instant.now());
		StepRun step=new StepRun("step-run-1","step-1"); step.startAttempt("attempt-1",Instant.now()).bindJob("job-1");
		PlanRun run=new PlanRun("run-1","approval-1",plan,List.of(step),Instant.now()); run.markRunning(Instant.now());
		var runSnapshot=roundTrip(PersistenceSnapshots.Run.of(run),PersistenceSnapshots.Run.class).value();
		assertEquals(PlanRunStatus.RUNNING,runSnapshot.getStatus()); assertEquals("job-1",runSnapshot.getSteps().getFirst().getCurrentAttempt().getJobId());
	}
	private <T>T roundTrip(T value,Class<T> type)throws Exception{return mapper.readValue(mapper.writeValueAsString(value),type);}
}
