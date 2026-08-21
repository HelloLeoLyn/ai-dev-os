package com.aidevos.orchestrator.diagnosis;

import java.time.Instant;
import java.util.Map;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Known Failure 记录服务。
 *
 * 幂等语义：occurrenceCount 按 fingerprint + taskId 去重——同一 Task 对同一
 * fingerprint 只计入一次（UI 刷新 N 次不增长）；FAILURE_DIAGNOSED 事件也只在
 * 首次计入该 task+fp 时产生一次。
 */
@Service
public class KnownFailureService {

	private final KnownFailureRepository repository;
	private volatile AuditService audit;

	public KnownFailureService(KnownFailureRepository repository) {
		this.repository = repository;
	}

	@Autowired(required = false)
	public void setAuditService(AuditService audit) {
		this.audit = audit;
	}

	/**
	 * 记录一次诊断。返回该 fingerprint 的 KnownFailure（新建或复用），
	 * 以及本次是否"新 task 首次计入"（用于判定 knownFailure 与事件）。
	 */
	public synchronized KnownFailureRecorded record(FailureDiagnosis diagnosis, String taskId) {
		Instant now = Instant.now();
		KnownFailure existing = repository.get(diagnosis.fingerprint());
		if (existing == null) {
			KnownFailure created = KnownFailure.first(diagnosis.fingerprint(), diagnosis.code(),
				diagnosis.category(), diagnosis.rootCause(), diagnosis.recommendedAction(),
				taskId, now);
			repository.save(created);
			emitEvent(diagnosis, taskId, false);
			return new KnownFailureRecorded(created, false, true);
		}
		if (existing.seenTaskIds().contains(taskId)) {
			// 同一 task 重复诊断：幂等，不计数、不写库、不发事件
			return new KnownFailureRecorded(existing, true, false);
		}
		KnownFailure updated = existing.withOccurrence(taskId, now);
		repository.save(updated);
		emitEvent(diagnosis, taskId, true);
		return new KnownFailureRecorded(updated, true, true);
	}

	private void emitEvent(FailureDiagnosis diagnosis, String taskId, boolean known) {
		if (audit == null) {
			return;
		}
		audit.taskEvent(EventType.FAILURE_DIAGNOSED, taskId, null, "DIAGNOSED",
			"Failure diagnosed: " + diagnosis.code(), Map.of(
				"fingerprint", diagnosis.fingerprint(),
				"code", diagnosis.code(),
				"category", diagnosis.category() == null ? "" : diagnosis.category().name(),
				"stage", diagnosis.stage() == null ? "" : diagnosis.stage(),
				"knownFailure", String.valueOf(known)));
	}

	/** record 结果：failure（当前 KnownFailure）+ knownFailure（是否历史命中）+ 是否本次新增计数。 */
	public record KnownFailureRecorded(KnownFailure failure, boolean knownFailure,
			boolean newlyCounted) {
	}
}
