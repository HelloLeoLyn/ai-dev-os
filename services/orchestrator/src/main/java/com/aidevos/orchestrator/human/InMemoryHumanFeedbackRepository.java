package com.aidevos.orchestrator.human;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

/**
 * In-memory human feedback store, ordered by creation time per task.
 */
@Repository
public class InMemoryHumanFeedbackRepository implements HumanFeedbackRepository {

	private final Map<String, HumanFeedback> feedbacks = new LinkedHashMap<>();

	@Override
	public synchronized void save(HumanFeedback feedback) {
		feedbacks.put(feedback.getFeedbackId(), feedback);
	}

	@Override
	public synchronized HumanFeedback get(String feedbackId) {
		return feedbacks.get(feedbackId);
	}

	@Override
	public synchronized List<HumanFeedback> listByTask(String taskId) {
		if (taskId == null) {
			return List.of();
		}
		return feedbacks.values().stream()
			.filter(feedback -> taskId.equals(feedback.getTaskId()))
			.toList();
	}

	@Override
	public synchronized List<HumanFeedback> list() {
		return List.copyOf(feedbacks.values());
	}
}
