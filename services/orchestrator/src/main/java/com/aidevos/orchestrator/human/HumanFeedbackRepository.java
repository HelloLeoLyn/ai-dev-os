package com.aidevos.orchestrator.human;

import java.util.List;

/**
 * Persistence contract for human feedback. Implemented by the in-memory
 * store; no database migration is introduced in this phase.
 */
public interface HumanFeedbackRepository {

	void save(HumanFeedback feedback);

	HumanFeedback get(String feedbackId);

	List<HumanFeedback> listByTask(String taskId);

	List<HumanFeedback> list();
}
