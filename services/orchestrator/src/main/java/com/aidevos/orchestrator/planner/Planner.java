package com.aidevos.orchestrator.planner;

import com.aidevos.orchestrator.planner.replan.ReplanRequest;

public interface Planner {

	String name();

	PlanDraft plan(PlanningRequest request);

	PlanDraft replan(ReplanRequest request);
}
