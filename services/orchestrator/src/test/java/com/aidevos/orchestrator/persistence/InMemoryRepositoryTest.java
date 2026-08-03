package com.aidevos.orchestrator.persistence;

import java.time.Instant;
import java.util.List;
import com.aidevos.orchestrator.approval.*;
import com.aidevos.orchestrator.execution.*;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.manager.*;
import com.aidevos.orchestrator.model.*;
import com.aidevos.orchestrator.plan.*;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.schedule.*;
import com.aidevos.orchestrator.task.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryRepositoryTest {
	@Test void preservesCrudAndStateSemantics() {
		TaskDefinition task=new TaskDefinition(); task.setId("task-1");
		TaskRepository tasks=new InMemoryTaskRepository(); tasks.save(task);
		assertSame(task,tasks.get("task-1")); tasks.remove("task-1"); assertNull(tasks.get("task-1"));

		AgentDefinition agent=new AgentDefinition(); agent.setName("agent-1");
		AgentRepository agents=new InMemoryAgentRepository(); agents.save(agent);
		assertEquals(List.of(agent),agents.getAll());

		ExecutionRecord record=new ExecutionRecord(); record.setId("record-1");
		ExecutionRecordRepository records=new InMemoryExecutionRecordRepository(); records.save(record);
		assertSame(record,records.get("record-1"));

		ScheduledTask schedule=new ScheduledTask(); schedule.setId("schedule-1");
		ScheduleRepository schedules=new InMemoryScheduleRepository(); schedules.save(schedule);
		assertSame(schedule,schedules.get("schedule-1"));

		ExecutionJob job=new ExecutionJob("job-1",task); JobRepository jobs=new JobStore(); jobs.save(job);
		assertEquals(List.of(job),jobs.getByStatus(JobStatus.QUEUED));

		CodingApprovalRequest approval=new CodingApprovalRequest("approval-1","task-1","job-1","/tmp","workspace-write","test");
		CodingApprovalRepository approvals=new ApprovalStore(); approvals.save(approval);
		assertSame(approval,approvals.findReusable("task-1","job-1"));

		Plan plan=new Plan("plan-1",1,"goal",PlanStatus.APPROVED,List.of(),List.of(),null,Instant.now());
		PlanRun run=new PlanRun("run-1","approval-1",plan,List.of(),Instant.now());
		PlanRunRepository runs=new InMemoryPlanRunRepository(); runs.create("approval-1",run);
		assertEquals("run-1",runs.findRunIdByApproval("approval-1"));
		assertThrows(IllegalStateException.class,()->runs.create("approval-1",run));
	}
}
