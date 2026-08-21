package com.aidevos.orchestrator.diagnosis;

import java.util.List;

/**
 * Known Failure 存储，按 fingerprint 标识。
 */
public interface KnownFailureRepository {

	void save(KnownFailure failure);

	KnownFailure get(String fingerprint);

	List<KnownFailure> list();
}
