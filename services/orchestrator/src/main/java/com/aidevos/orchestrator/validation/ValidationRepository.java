package com.aidevos.orchestrator.validation;

import java.util.List;

public interface ValidationRepository {
	void save(ValidationRun run);
	ValidationRun get(String validationRunId);
	List<ValidationRun> findByTaskId(String taskId);
	List<ValidationRun> list();
}
