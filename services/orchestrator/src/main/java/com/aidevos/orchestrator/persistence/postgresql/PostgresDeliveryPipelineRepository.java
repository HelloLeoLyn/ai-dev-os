package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;

import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the delivery pipeline repository backed by the
 * shared repository_documents store. Stage progress, entity bindings and
 * failure state survive service restarts, so advance() resumes instead of
 * re-executing completed work.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresDeliveryPipelineRepository implements DeliveryPipelineRepository {

	private static final String TYPE = "delivery-pipeline";

	private final PostgresDocumentStore store;

	PostgresDeliveryPipelineRepository(PostgresDocumentStore store) {
		this.store = store;
	}

	@Override
	public void save(DeliveryPipeline pipeline) {
		store.put(TYPE, pipeline.getTaskId(),
			PersistenceSnapshots.DeliveryPipeline.of(pipeline), "task:" + pipeline.getTaskId());
	}

	@Override
	public DeliveryPipeline get(String taskId) {
		PersistenceSnapshots.DeliveryPipeline snapshot = store.get(TYPE, taskId,
			PersistenceSnapshots.DeliveryPipeline.class);
		return snapshot == null ? null : snapshot.value();
	}

	@Override
	public List<DeliveryPipeline> list() {
		return store.all(TYPE, PersistenceSnapshots.DeliveryPipeline.class).stream()
			.map(PersistenceSnapshots.DeliveryPipeline::value).toList();
	}
}
