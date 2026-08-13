package com.aidevos.orchestrator.validation.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.ci.CiRepository;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.springframework.stereotype.Component;

@Component
public class CiValidationProvider implements ValidationProvider {
	private final CiRepository repository;
	public CiValidationProvider(CiRepository repository) { this.repository = repository; }
	@Override public boolean supports(ValidationContext context) {
		return context.type() == ValidationCheckType.CI;
	}
	@Override public ValidationCheckResult execute(ValidationContext context) {
		CiRunRecord run = repository.getByTaskId(context.taskId()).stream()
			.max(Comparator.comparing(CiRunRecord::getStartedAt,
				Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
		if (run == null) return ValidationCheckResult.skipped("CI not available for this task");
		ValidationStatus status = switch (run.getStatus()) {
			case SUCCESS -> ValidationStatus.SUCCESS;
			case FAILED, CANCELLED -> ValidationStatus.FAILED;
			case RUNNING -> ValidationStatus.RUNNING;
			case PENDING -> ValidationStatus.PENDING;
		};
		return new ValidationCheckResult(status, "CI " + run.getStatus().name().toLowerCase(),
			status == ValidationStatus.FAILED ? "CI run " + run.getStatus().name().toLowerCase() : null,
			null, null, run.getReportUrl().isBlank() ? List.of() : List.of(run.getReportUrl()),
			Map.of("ciRunId", run.getCiRunId(), "provider", run.getProvider(),
				"reportUrl", run.getReportUrl()));
	}
	@Override public String name() { return "existing-ci"; }
}
