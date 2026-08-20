package com.aidevos.orchestrator.delivery;

import java.util.List;

/**
 * Store for delivery pipeline aggregates, keyed by task id and queryable for
 * observability. Implementations must persist every save so a restart can
 * resume from the recorded stage without re-executing completed work.
 */
public interface DeliveryPipelineRepository {

	void save(DeliveryPipeline pipeline);

	DeliveryPipeline get(String taskId);

	List<DeliveryPipeline> list();
}
