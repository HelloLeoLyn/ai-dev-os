package com.aidevos.orchestrator.dashboard;

import java.util.List;

import com.aidevos.orchestrator.audit.query.AuditEventView;

/**
 * Unified timeline for the dashboard. Scope is resolved automatically to a
 * PlanRun, Job or Execution when the id matches one, otherwise audit events
 * for the aggregate/step are returned.
 */
public record DashboardTimeline(
		String scopeType,
		String scopeId,
		List<AuditEventView> events) {

	public DashboardTimeline {
		events = List.copyOf(events);
	}
}
