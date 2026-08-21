package com.aidevos.orchestrator.validationplan;

import java.util.List;

import com.aidevos.orchestrator.validationplan.ValidationExecutionModels.ValidationRunResult;

/**
 * ValidationRun 结果存储（reuse 判定用）。
 */
public interface ValidationRunResultRepository {

	void save(ValidationRunResult run);

	/** 同 task + 同 change fingerprint + 同 plan fingerprint 且 SUCCESS 的历史 run（reuse 候选）。 */
	ValidationRunResult findReusable(String taskId, String changeFingerprint,
			String planFingerprint);

	List<ValidationRunResult> list();
}
