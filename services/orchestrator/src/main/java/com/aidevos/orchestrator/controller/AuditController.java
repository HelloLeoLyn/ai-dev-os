package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.query.AuditEventPage;
import com.aidevos.orchestrator.audit.query.AuditQueryService;
import com.aidevos.orchestrator.audit.timeline.ExecutionTimeline;
import com.aidevos.orchestrator.audit.timeline.TimelineService;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuditController {
	private final AuditQueryService auditQueryService;
	private final TimelineService timelineService;

	public AuditController(AuditQueryService auditQueryService, TimelineService timelineService) {
		this.auditQueryService = auditQueryService;
		this.timelineService = timelineService;
	}

	@GetMapping("/audit/events")
	public AuditEventPage events(
			@RequestParam(required = false) String aggregateType,
			@RequestParam(required = false) String aggregateId,
			@RequestParam(required = false) String planRunId,
			@RequestParam(required = false) String stepRunId,
			@RequestParam(required = false) String attemptId,
			@RequestParam(required = false) String jobId,
			@RequestParam(required = false) String executionId,
			@RequestParam(required = false) String executionRecordId,
			@RequestParam(required = false) String invocationId,
			@RequestParam(required = false) String approvalId,
			@RequestParam(required = false, defaultValue = "") Set<EventType> eventTypes,
			@RequestParam(required = false) Instant occurredAfter,
			@RequestParam(required = false) Instant occurredBefore,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "100") int limit) {
		return auditQueryService.query(new EventQuery(aggregateType, aggregateId, planRunId,
			stepRunId, attemptId, jobId, executionId, executionRecordId, invocationId, approvalId,
			eventTypes, occurredAfter, occurredBefore, offset, limit));
	}

	@GetMapping("/timelines/plan-runs/{id}")
	public ExecutionTimeline planRun(@PathVariable String id,
			@RequestParam(required = false, defaultValue = "") Set<EventType> eventTypes,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "100") int limit) {
		return timelineService.planRun(id, eventTypes, offset, limit);
	}

	@GetMapping("/timelines/executions/{id}")
	public ExecutionTimeline execution(@PathVariable String id,
			@RequestParam(required = false, defaultValue = "") Set<EventType> eventTypes,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "100") int limit) {
		return timelineService.execution(id, eventTypes, offset, limit);
	}

	@GetMapping("/timelines/jobs/{id}")
	public ExecutionTimeline job(@PathVariable String id,
			@RequestParam(required = false, defaultValue = "") Set<EventType> eventTypes,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "100") int limit) {
		return timelineService.job(id, eventTypes, offset, limit);
	}

}
