package com.aidevos.orchestrator.observability.usage;

import java.util.List;

/**
 * Persistence contract for usage records.
 */
public interface UsageRepository {

	void save(UsageRecord record);

	List<UsageRecord> list();
}
