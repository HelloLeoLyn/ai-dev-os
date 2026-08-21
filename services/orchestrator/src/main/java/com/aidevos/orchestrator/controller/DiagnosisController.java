package com.aidevos.orchestrator.controller;

import com.aidevos.orchestrator.diagnosis.FailureDiagnosis;
import com.aidevos.orchestrator.diagnosis.FailureDiagnosisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1 Failure Diagnosis API。
 *
 * GET /api/tasks/{taskId}/diagnosis
 * 成功返回 FailureDiagnosis；Task 无失败（或处于正常人工 Gate）时明确返回 no active failure（body null）。
 * 按需同步生成，本地数据 <1s，无异步 Job。
 */
@RestController
@RequestMapping("/api/tasks")
public class DiagnosisController {

	private final FailureDiagnosisService diagnosisService;

	public DiagnosisController(FailureDiagnosisService diagnosisService) {
		this.diagnosisService = diagnosisService;
	}

	@GetMapping("/{taskId}/diagnosis")
	public ResponseEntity<FailureDiagnosis> diagnose(@PathVariable String taskId) {
		FailureDiagnosis diagnosis = diagnosisService.diagnose(taskId);
		if (diagnosis == null) {
			// 明确返回 no active failure，不伪造 diagnosis
			return ResponseEntity.ok().body(null);
		}
		return ResponseEntity.ok(diagnosis);
	}
}
