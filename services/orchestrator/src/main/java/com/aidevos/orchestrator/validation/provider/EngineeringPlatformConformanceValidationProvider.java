package com.aidevos.orchestrator.validation.provider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformAdapter;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformProperties;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformResult;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformStatus;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.springframework.stereotype.Component;

@Component
public class EngineeringPlatformConformanceValidationProvider implements ValidationProvider {

	private static final Duration TIMEOUT = Duration.ofMinutes(10);
	private final EngineeringPlatformAdapter adapter;
	private final EngineeringPlatformProperties properties;

	public EngineeringPlatformConformanceValidationProvider(EngineeringPlatformAdapter adapter,
			EngineeringPlatformProperties properties) {
		this.adapter = adapter;
		this.properties = properties;
	}

	@Override
	public boolean supports(ValidationContext context) {
		return properties.isEnabled() && context.type() == ValidationCheckType.CONTRACT
			&& context.capabilities().containsKey("engineeringPlatformProjectYaml");
	}

	@Override
	public ValidationCheckResult execute(ValidationContext context) {
		Path manifest = Path.of(context.capabilities().get("engineeringPlatformProjectYaml").toString());
		EngineeringPlatformResult result = adapter.conformance(manifest, context.workspacePath(),
			context.workspacePath(), TIMEOUT);
		ValidationStatus status = validationStatus(result.status());
		String summary = status == ValidationStatus.SUCCESS
			? "Engineering conformance passed" : "Engineering conformance did not pass";
		String error = status == ValidationStatus.SUCCESS ? null : diagnostic(result);
		return new ValidationCheckResult(status, summary, error, result.stdout(), result.stderr(),
			List.of(), result.commandMetadata());
	}

	@Override
	public String name() { return "engineering-platform-conformance"; }

	private ValidationStatus validationStatus(EngineeringPlatformStatus status) {
		return switch (status) {
			case SUCCESS -> ValidationStatus.SUCCESS;
			case DOMAIN_FAILURE -> ValidationStatus.FAILED;
			case USAGE_ERROR, EXECUTION_ERROR -> ValidationStatus.ERROR;
		};
	}

	private String diagnostic(EngineeringPlatformResult result) {
		if (result.stderr() != null && !result.stderr().isBlank()) return result.stderr();
		if (result.stdout() != null && !result.stdout().isBlank()) return result.stdout();
		return "Engineering Platform conformance failed with exit " + result.exitCode();
	}
}
