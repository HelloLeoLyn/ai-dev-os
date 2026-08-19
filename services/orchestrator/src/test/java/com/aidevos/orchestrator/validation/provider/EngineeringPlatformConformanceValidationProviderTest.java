package com.aidevos.orchestrator.validation.provider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformAdapter;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformOperation;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformProperties;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformResult;
import com.aidevos.orchestrator.engineeringplatform.EngineeringPlatformStatus;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringPlatformConformanceValidationProviderTest {

	@Test void mapsPassFailAndUsageError() {
		assertEquals(ValidationStatus.SUCCESS, execute(EngineeringPlatformStatus.SUCCESS, 0).status());
		assertEquals(ValidationStatus.FAILED, execute(EngineeringPlatformStatus.DOMAIN_FAILURE, 1).status());
		assertEquals(ValidationStatus.ERROR, execute(EngineeringPlatformStatus.USAGE_ERROR, 2).status());
	}

	@Test void supportsOnlyEnabledContractWithManifest() {
		EngineeringPlatformProperties properties = new EngineeringPlatformProperties();
		EngineeringPlatformConformanceValidationProvider provider = provider(properties,
			EngineeringPlatformStatus.SUCCESS, 0);
		assertFalse(provider.supports(context()));
		properties.setEnabled(true);
		assertTrue(provider.supports(context()));
	}

	private ValidationCheckResult execute(EngineeringPlatformStatus status, int exitCode) {
		EngineeringPlatformProperties properties = new EngineeringPlatformProperties();
		properties.setEnabled(true);
		return provider(properties, status, exitCode).execute(context());
	}

	private EngineeringPlatformConformanceValidationProvider provider(
			EngineeringPlatformProperties properties, EngineeringPlatformStatus status, int exitCode) {
		EngineeringPlatformAdapter adapter = new EngineeringPlatformAdapter() {
			@Override public EngineeringPlatformResult validate(Path manifest, Path workspace, Duration timeout) { return result(); }
			@Override public EngineeringPlatformResult resolve(Path manifest, Path workspace, Duration timeout) { return result(); }
			@Override public EngineeringPlatformResult generate(Path manifest, Path output, Path workspace, Duration timeout) { return result(); }
			@Override public EngineeringPlatformResult conformance(Path manifest, Path projectDirectory, Path workspace, Duration timeout) { return result(); }
			private EngineeringPlatformResult result() { return new EngineeringPlatformResult(
				EngineeringPlatformOperation.CONFORMANCE, exitCode, status, "diagnostic", "", 4,
				Map.of("operation", "CONFORMANCE", "exitCode", exitCode, "durationMs", 4)); }
		};
		return new EngineeringPlatformConformanceValidationProvider(adapter, properties);
	}

	private ValidationContext context() {
		return new ValidationContext("run-1", "task-1", "project-1", "workspace-1", Path.of("."),
			ValidationCheckType.CONTRACT, Map.of("engineeringPlatformProjectYaml", "project.yaml"), false);
	}
}
