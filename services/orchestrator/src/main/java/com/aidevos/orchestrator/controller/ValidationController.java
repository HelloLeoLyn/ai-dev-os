package com.aidevos.orchestrator.controller;

import java.util.List;

import com.aidevos.orchestrator.common.exception.ResourceNotFoundException;
import com.aidevos.orchestrator.validation.ValidationArtifact;
import com.aidevos.orchestrator.validation.ValidationEvidenceService;
import com.aidevos.orchestrator.validation.ValidationRun;
import com.aidevos.orchestrator.validation.ValidationService;
import com.aidevos.orchestrator.validation.security.SecurityFinding;
import com.aidevos.orchestrator.validation.security.SecurityReport;
import com.aidevos.orchestrator.validation.security.SecurityValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidationController {
	private final ValidationService service;
	private final ValidationEvidenceService evidenceService;
	private final SecurityValidationService securityService;

	public ValidationController(ValidationService service, ValidationEvidenceService evidenceService,
			SecurityValidationService securityService) {
		this.service = service;
		this.evidenceService = evidenceService;
		this.securityService = securityService;
	}

	@GetMapping("/api/validations/{validationRunId}/security-reports")
	public List<SecurityReport> securityReports(@PathVariable String validationRunId) {
		service.get(validationRunId); return securityService.byRun(validationRunId);
	}

	@GetMapping("/api/security-reports/{reportId}")
	public SecurityReport securityReport(@PathVariable String reportId) { return securityService.get(reportId); }

	@GetMapping("/api/security-reports/{reportId}/findings")
	public List<SecurityFinding> findings(@PathVariable String reportId) { return securityService.get(reportId).getFindings(); }

	@PostMapping("/api/tasks/{taskId}/validations")
	public ResponseEntity<ValidationRun> start(@PathVariable String taskId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.start(taskId));
	}

	@GetMapping("/api/tasks/{taskId}/validations")
	public List<ValidationRun> taskHistory(@PathVariable String taskId) {
		return service.findByTask(taskId);
	}

	@GetMapping("/api/validations")
	public List<ValidationRun> list() { return service.list(); }

	@GetMapping("/api/validations/{validationRunId}")
	public ValidationRun get(@PathVariable String validationRunId) { return service.get(validationRunId); }

	@GetMapping("/api/validation-artifacts/{artifactId}")
	public ValidationArtifact artifact(@PathVariable String artifactId) {
		ValidationArtifact artifact = evidenceService.get(artifactId);
		if (artifact == null) throw new ResourceNotFoundException("ValidationArtifact", artifactId);
		return artifact;
	}
}
