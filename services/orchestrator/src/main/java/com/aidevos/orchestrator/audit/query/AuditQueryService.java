package com.aidevos.orchestrator.audit.query;

import com.aidevos.orchestrator.audit.AuditRepository;
import com.aidevos.orchestrator.audit.EventQuery;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
	private final AuditRepository repository;

	public AuditQueryService(AuditRepository repository) {
		this.repository = repository;
	}

	public AuditEventPage query(EventQuery query) {
		List<AuditEventView> events = repository.query(query).stream()
			.map(AuditEventView::from).toList();
		return new AuditEventPage(query.offset(), query.limit(), events.size(), events);
	}
}
