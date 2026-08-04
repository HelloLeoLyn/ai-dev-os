package com.aidevos.orchestrator.persistence.postgresql;

import com.aidevos.orchestrator.approval.*;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.task.PostgresTaskRepository;
import com.aidevos.orchestrator.manager.PostgresAgentRepository;
import com.aidevos.orchestrator.execution.PostgresExecutionRecordRepository;
import com.aidevos.orchestrator.schedule.*;
import com.aidevos.orchestrator.tool.approval.*;
import com.aidevos.orchestrator.plan.*;
import com.aidevos.orchestrator.plan.approval.*;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.planner.replan.*;
import com.aidevos.orchestrator.execution.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIntegrationTest {
	@Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
	@Test void persistsAndReloadsRepositoriesAcrossInstances() {
		PGSimpleDataSource dataSource=new PGSimpleDataSource(); dataSource.setUrl(POSTGRES.getJdbcUrl()); dataSource.setUser(POSTGRES.getUsername()); dataSource.setPassword(POSTGRES.getPassword());
		PostgresDocumentStore documents=new PostgresDocumentStore(dataSource,new ObjectMapper());
		PostgresLeaseableJobRepository jobs=new PostgresLeaseableJobRepository(dataSource,new ObjectMapper());
		TaskDefinition task=new TaskDefinition(); task.setId("task-1"); ExecutionJob job=new ExecutionJob("job-1",task); job.markRunning(); jobs.save(job);
		assertEquals(JobStatus.RUNNING,new PostgresLeaseableJobRepository(dataSource,new ObjectMapper()).get("job-1").getStatus());

		ExecutionJob queued=new ExecutionJob("job-2",task); queued.setMaxAttempts(3); jobs.save(queued);
		Instant now=Instant.parse("2026-08-04T00:00:00Z");
		Optional<JobLease> lease=jobs.claimNext(now,"worker-1",Duration.ofSeconds(60));
		assertTrue(lease.isPresent()); assertEquals(1,lease.get().token());
		ExecutionJob claimed=new PostgresLeaseableJobRepository(dataSource,new ObjectMapper()).get("job-2");
		assertEquals(JobStatus.RUNNING,claimed.getStatus()); assertEquals(1,claimed.getAttemptNo());
		assertEquals("worker-1",claimed.getLeaseOwner()); assertEquals(Long.valueOf(1),claimed.getLeaseToken());
		assertEquals(1,claimed.getVersion()); assertEquals(3,claimed.getMaxAttempts());

		PostgresExecutionAttemptRepository attempts=new PostgresExecutionAttemptRepository(dataSource);
		ExecutionAttempt attempt=new ExecutionAttempt("attempt-1","job-2",1); attempt.markRunning(now); attempt.setExecutionId("exec-1"); attempt.applyLease(new JobLease("worker-1",1,now.minusSeconds(30))); attempts.save(attempt);
		ExecutionAttempt storedAttempt=new PostgresExecutionAttemptRepository(dataSource).get("attempt-1");
		assertEquals(ExecutionAttemptStatus.RUNNING,storedAttempt.getStatus()); assertEquals("exec-1",storedAttempt.getExecutionId());
		assertEquals("attempt-1",attempts.findAbandoned(now).getFirst().getId());

		PostgresCodingApprovalRepository approvals=new PostgresCodingApprovalRepository(documents);
		CodingApprovalRequest approval=new CodingApprovalRequest("approval-1","task-1","job-1","/work","workspace-write","test"); approval.approve(); approvals.save(approval);
		assertEquals(ApprovalStatus.APPROVED,new PostgresCodingApprovalRepository(documents).get("approval-1").getStatus());

		TaskDefinition persistedTask=new TaskDefinition(); persistedTask.setId("task-2"); new PostgresTaskRepository(documents).save(persistedTask);
		assertEquals("task-2",new PostgresTaskRepository(documents).get("task-2").getId());
		AgentDefinition agent=new AgentDefinition(); agent.setName("agent-1"); new PostgresAgentRepository(documents).save(agent);
		assertEquals("agent-1",new PostgresAgentRepository(documents).get("agent-1").getName());
		ExecutionRecord record=new ExecutionRecord(); record.setId("record-1"); new PostgresExecutionRecordRepository(documents).save(record);
		assertEquals("record-1",new PostgresExecutionRecordRepository(documents).get("record-1").getId());
		ScheduledTask schedule=new ScheduledTask(); schedule.setId("schedule-1"); schedule.setTaskId("task-2"); new PostgresScheduleRepository(documents).save(schedule);
		assertEquals("task-2",new PostgresScheduleRepository(documents).get("schedule-1").getTaskId());

		ToolApprovalRequest toolApproval=new ToolApprovalRequest("tool-approval-1","execution-1","invocation-1","job-1","mcp","write","hash","/work","workspace-write","test");
		toolApproval.approve(); new PostgresToolApprovalRepository(documents).save(toolApproval);
		assertEquals(ApprovalStatus.APPROVED,new PostgresToolApprovalRepository(documents).get("tool-approval-1").getStatus());

		Plan plan=new Plan("plan-1",1,"goal",PlanStatus.APPROVED,List.of(),List.of(),null,Instant.now());
		PlanApprovalRequest planApproval=new PlanApprovalRequest("plan-approval-1","request-1",plan,"hash",Instant.now()); planApproval.approve("tester",Instant.now());
		new PostgresPlanApprovalRepository(documents).save(planApproval);
		assertEquals(ApprovalStatus.APPROVED,new PostgresPlanApprovalRepository(documents).get("plan-approval-1").getStatus());
		PlanRun run=new PlanRun("run-1","plan-approval-1",plan,List.of(),Instant.now()); run.markRunning(Instant.now());
		PostgresPlanRunRepository runs=new PostgresPlanRunRepository(documents); runs.create("plan-approval-1",run);
		assertEquals("run-1",new PostgresPlanRunRepository(documents).findRunIdByApproval("plan-approval-1"));
		assertThrows(IllegalStateException.class,()->runs.create("plan-approval-1",run));

		ReplanRequest replan=new ReplanRequest("replan-1","plan-1",1,"run-1","step-1",FailureClassification.UNKNOWN,"test",List.of(),null,List.of(),plan,Instant.now());
		new PostgresReplanRequestRepository(documents).save(replan);
		assertEquals("replan-1",new PostgresReplanRequestRepository(documents).findByPlanRun("run-1").id());
	}
}
