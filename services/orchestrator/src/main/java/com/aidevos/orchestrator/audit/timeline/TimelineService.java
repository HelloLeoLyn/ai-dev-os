package com.aidevos.orchestrator.audit.timeline;

import com.aidevos.orchestrator.audit.AuditRepository;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.query.AuditEventView;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TimelineService {
	private final AuditRepository repository;

	public TimelineService(AuditRepository repository) {
		this.repository = repository;
	}

	public ExecutionTimeline planRun(String id, Set<EventType> types, int offset, int limit) {
		return timeline(TimelineScopeType.PLAN_RUN, id, query(id, null, null, types, offset, limit));
	}

	public ExecutionTimeline execution(String id, Set<EventType> types, int offset, int limit) {
		return timeline(TimelineScopeType.EXECUTION, id, query(null, id, null, types, offset, limit));
	}

	public ExecutionTimeline job(String id, Set<EventType> types, int offset, int limit) {
		return timeline(TimelineScopeType.JOB, id, query(null, null, id, types, offset, limit));
	}

	private EventQuery query(String planRunId, String executionId, String jobId,
			Set<EventType> types, int offset, int limit) {
		return new EventQuery(null, null, planRunId, null, null, jobId, executionId, null,
			null, null, types, null, null, offset, limit);
	}

	private ExecutionTimeline timeline(TimelineScopeType scope, String id, EventQuery query) {
		if (id == null || id.isBlank()) throw new IllegalArgumentException("Timeline id is required");
		List<AuditEventView> events = repository.query(query).stream()
			.map(AuditEventView::from).toList();
		return new ExecutionTimeline(scope, id, query.offset(), query.limit(), events.size(), events);
	}
}
