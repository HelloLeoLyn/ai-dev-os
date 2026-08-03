package com.aidevos.orchestrator.planner.replan;

import com.aidevos.orchestrator.persistence.CrudRepository;

public interface ReplanRequestRepository extends CrudRepository<ReplanRequest> {
	ReplanRequest findByPlanRun(String planRunId);
}
