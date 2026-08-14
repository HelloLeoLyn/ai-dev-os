package com.aidevos.orchestrator.timeline;

import java.util.Map;
import java.util.Optional;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.job.JobRepository;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.task.TaskManager;
import com.aidevos.orchestrator.taskcenter.TaskCenterService;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacklogTimelineTest {
	@Test void conversionIsVisibleFromBacklogAndFormalTaskTimeline() {
		InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
		new AuditService(auditRepository).backlogEvent(EventType.BACKLOG_CONVERTED_TO_TASK,
			"backlog-1", "task-1", "READY", "CONVERTED", "Converted", Map.of());
		TaskCenterService taskCenter = mock(TaskCenterService.class);
		when(taskCenter.getTask("backlog-1")).thenReturn(Optional.empty());
		when(taskCenter.getTask("task-1")).thenReturn(Optional.of(new TaskRecord("task-1", "Task", null)));
		TimelineService service = new TimelineService(auditRepository, mock(PlanRunRepository.class),
			mock(JobRepository.class), mock(ExecutionRecordRepository.class), mock(TaskManager.class), taskCenter);
		UnifiedTimeline backlog = service.timeline("backlog-1");
		UnifiedTimeline task = service.timeline("task-1");
		assertEquals("BACKLOG", backlog.events().getFirst().sourceType());
		assertEquals("backlog-1", backlog.events().getFirst().sourceId());
		assertEquals(EventType.BACKLOG_CONVERTED_TO_TASK.name(), task.events().getFirst().eventType());
		assertEquals("BACKLOG", task.events().getFirst().sourceType());
	}
}
