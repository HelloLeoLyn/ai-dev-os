package com.aidevos.orchestrator.qualitygate;
import java.util.List;
public interface QualityGateRepository { void save(QualityGateResult result); QualityGateResult get(String id); List<QualityGateResult> findByValidationRunId(String runId); }
