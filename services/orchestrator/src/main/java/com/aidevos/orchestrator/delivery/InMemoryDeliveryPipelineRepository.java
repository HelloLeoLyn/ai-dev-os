package com.aidevos.orchestrator.delivery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory delivery pipeline store (default profile). Keeps the same save
 * semantics as the PostgreSQL implementation: every mutation is persisted
 * through save().
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory",
	matchIfMissing = true)
public class InMemoryDeliveryPipelineRepository implements DeliveryPipelineRepository {

	private final Map<String, DeliveryPipeline> pipelines = new LinkedHashMap<>();

	@Override
	public synchronized void save(DeliveryPipeline pipeline) {
		pipelines.put(pipeline.getTaskId(), pipeline);
	}

	@Override
	public synchronized DeliveryPipeline get(String taskId) {
		return pipelines.get(taskId);
	}

	@Override
	public synchronized List<DeliveryPipeline> list() {
		return new ArrayList<>(pipelines.values());
	}
}
