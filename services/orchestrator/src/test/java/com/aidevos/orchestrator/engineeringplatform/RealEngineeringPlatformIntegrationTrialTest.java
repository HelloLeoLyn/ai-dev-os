package com.aidevos.orchestrator.engineeringplatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import com.aidevos.orchestrator.validation.ValidationStatus;
import com.aidevos.orchestrator.validation.provider.MavenValidationProvider;
import com.aidevos.orchestrator.validation.provider.ValidationCheckResult;
import com.aidevos.orchestrator.validation.provider.ValidationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real CLI trial; skips only when the sibling Engineering Platform checkout is unavailable. */
class RealEngineeringPlatformIntegrationTrialTest {

	private static final Path EP_ROOT = Path.of("/home/administrator/workspace/engineering-platform");
	@TempDir Path workspace;

	@Test void executesV030CliThenExistingBuildAndTestValidation() throws Exception {
		assumeTrue(Runtime.version().feature() >= 25,
			"Engineering Platform v0.3.0 generated projects require Java 25");
		Path executable = EP_ROOT.resolve("ep");
		Path sourceManifest = EP_ROOT.resolve("tests/fixtures/v03-reference/inventory-service/project.yaml");
		assumeTrue(Files.isRegularFile(executable) && Files.isRegularFile(sourceManifest),
			"Engineering Platform v0.3.0 checkout is not available");
		Path manifest = Files.copy(sourceManifest, workspace.resolve("project.yaml"));
		Path generated = workspace.resolve("generated");
		EngineeringPlatformProperties properties = new EngineeringPlatformProperties();
		properties.setEnabled(true);
		properties.setExecutable(executable.toString());
		properties.setPlatformRoot(EP_ROOT.toString());
		properties.setVersion("v0.3.0");
		CommandExecutor executor = new CommandExecutor();
		EngineeringPlatformAdapter adapter = new CommandEngineeringPlatformAdapter(executor, properties);

		assertEquals(EngineeringPlatformStatus.SUCCESS,
			adapter.validate(manifest, workspace, Duration.ofMinutes(2)).status());
		assertEquals(EngineeringPlatformStatus.SUCCESS,
			adapter.resolve(manifest, workspace, Duration.ofMinutes(2)).status());
		assertEquals(EngineeringPlatformStatus.SUCCESS,
			adapter.generate(manifest, generated, workspace, Duration.ofMinutes(2)).status());
		assertEquals(EngineeringPlatformStatus.SUCCESS,
			adapter.conformance(manifest, generated, workspace, Duration.ofMinutes(2)).status());
		assertTrue(Files.isRegularFile(generated.resolve("pom.xml")));

		MavenValidationProvider maven = new MavenValidationProvider(executor);
		ValidationCheckResult test = maven.execute(context(generated, ValidationCheckType.BACKEND_TEST));
		ValidationCheckResult build = maven.execute(context(generated, ValidationCheckType.BACKEND_BUILD));
		assertEquals(ValidationStatus.SUCCESS, test.status(), test.errorMessage());
		assertEquals(ValidationStatus.SUCCESS, build.status(), build.errorMessage());
	}

	private ValidationContext context(Path generated, ValidationCheckType type) {
		return new ValidationContext("trial-run", "trial-task", "trial-project", "trial-workspace",
			workspace, type, Map.of("mavenDirectory", generated.toString()), false);
	}
}
