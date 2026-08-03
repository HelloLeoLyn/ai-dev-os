package com.aidevos.orchestrator.audit.timeline;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.audit.query.AuditQueryServiceTest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineServiceTest {
	@Test
	void returnsPlanRunExecutionAndJobTimelines() {
		InMemoryAuditRepository repository = new InMemoryAuditRepository();
		repository.append(AuditQueryServiceTest.event("1", EventType.JOB_STARTED, "run-1",
			"job-1", "exec-1", "2026-08-03T01:00:00Z"));
		repository.append(AuditQueryServiceTest.event("2", EventType.JOB_SUCCEEDED, "run-1",
			"job-1", "exec-1", "2026-08-03T01:00:01Z"));
		repository.append(AuditQueryServiceTest.event("3", EventType.JOB_FAILED, "run-2",
			"job-2", "exec-2", "2026-08-03T01:00:02Z"));
		TimelineService service = new TimelineService(repository);

		ExecutionTimeline planRun = service.planRun("run-1", Set.of(), 0, 100);
		ExecutionTimeline execution = service.execution("exec-1", Set.of(EventType.JOB_SUCCEEDED),
			0, 100);
		ExecutionTimeline job = service.job("job-1", Set.of(), 1, 1);

		assertEquals(TimelineScopeType.PLAN_RUN, planRun.scopeType());
		assertEquals(2, planRun.count());
		assertEquals(EventType.JOB_STARTED, planRun.events().getFirst().type());
		assertEquals(TimelineScopeType.EXECUTION, execution.scopeType());
		assertEquals(1, execution.count());
		assertEquals(TimelineScopeType.JOB, job.scopeType());
		assertEquals(EventType.JOB_SUCCEEDED, job.events().getFirst().type());
	}

	@Test
	void returnsEmptyTimeline() {
		ExecutionTimeline timeline = new TimelineService(new InMemoryAuditRepository())
			.job("missing", Set.of(), 0, 100);
		assertEquals(0, timeline.count());
		assertTrue(timeline.events().isEmpty());
	}
}
