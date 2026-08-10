package com.aidevos.orchestrator.feedback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * In-memory feedback record store. Intentionally in-memory only (no database
 * migration is introduced); a PostgreSQL-backed implementation can be added
 * later without touching the service.
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", matchIfMissing = true)
public class InMemoryFeedbackRepository implements FeedbackRepository {

	private final Map<String, PrFeedbackRecord> records = new LinkedHashMap<>();

	@Override
	public synchronized void save(PrFeedbackRecord record) {
		records.put(record.getFeedbackId(), record);
	}

	@Override
	public synchronized PrFeedbackRecord get(String feedbackId) {
		return records.get(feedbackId);
	}

	@Override
	public synchronized List<PrFeedbackRecord> getByTaskId(String taskId) {
		List<PrFeedbackRecord> result = new ArrayList<>();
		for (PrFeedbackRecord record : records.values()) {
			if (taskId != null && taskId.equals(record.getTaskId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<PrFeedbackRecord> getByPullRequestId(String pullRequestId) {
		List<PrFeedbackRecord> result = new ArrayList<>();
		for (PrFeedbackRecord record : records.values()) {
			if (pullRequestId != null && pullRequestId.equals(record.getPullRequestId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<PrFeedbackRecord> getByCiRunId(String ciRunId) {
		List<PrFeedbackRecord> result = new ArrayList<>();
		for (PrFeedbackRecord record : records.values()) {
			if (ciRunId != null && ciRunId.equals(record.getCiRunId())) {
				result.add(record);
			}
		}
		return result;
	}

	@Override
	public synchronized List<PrFeedbackRecord> list() {
		return new ArrayList<>(records.values());
	}
}
