package com.aidevos.orchestrator.controller;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

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
import org.springframework.web.bind.annotation.RequestParam;
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
	public ResponseEntity<ValidationRun> start(@PathVariable String taskId,
			@RequestParam(required = false) String scenarioId) {
		ValidationRun run = scenarioId == null || scenarioId.isBlank()
			? service.start(taskId) : service.start(taskId, scenarioId);
		return ResponseEntity.status(HttpStatus.CREATED).body(run);
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

	@GetMapping("/api/validation-artifacts/{artifactId}/content")
	public ResponseEntity<byte[]> artifactContent(@PathVariable String artifactId) {
		ValidationArtifact artifact = artifact(artifactId);
		if (!"image/png".equals(artifact.getMediaType()) || artifact.getUri() == null)
			throw new IllegalArgumentException("Artifact is not a previewable PNG");
		try {
			Path path = artifact.getUri().startsWith("file:")
				? Path.of(java.net.URI.create(artifact.getUri())) : Path.of(artifact.getUri());
			if (!Files.isRegularFile(path) || Files.size(path) > 10 * 1024 * 1024)
				throw new IllegalArgumentException("Screenshot is unavailable or too large");
			byte[] content = Files.readAllBytes(path);
			if (content.length < 8 || content[0] != (byte) 0x89 || content[1] != 0x50
					|| content[2] != 0x4e || content[3] != 0x47)
				throw new IllegalArgumentException("Screenshot content is not PNG");
			return ResponseEntity.ok().header("Content-Type", "image/png")
				.header("Cache-Control", "no-store").body(content);
		}
		catch (java.io.IOException exception) {
			throw new IllegalArgumentException("Screenshot cannot be read", exception);
		}
	}
}
