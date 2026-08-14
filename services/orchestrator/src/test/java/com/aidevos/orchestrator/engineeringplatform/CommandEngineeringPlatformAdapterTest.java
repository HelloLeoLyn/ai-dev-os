package com.aidevos.orchestrator.engineeringplatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandEngineeringPlatformAdapterTest {

	@TempDir Path root;
	private CommandExecutor executor;
	private EngineeringPlatformProperties properties;

	@BeforeEach
	void setUp() throws Exception {
		executor = mock(CommandExecutor.class);
		properties = new EngineeringPlatformProperties();
		properties.setEnabled(true);
		properties.setExecutable(Files.createFile(root.resolve("ep")).toString());
		properties.setPlatformRoot(root.toString());
		properties.setVersion("v0.3.0");
	}

	@Test void mapsValidateSuccessDomainFailureAndUsageError() {
		assertEquals(EngineeringPlatformStatus.SUCCESS, executeValidate(command(0, "ok", "")).status());
		assertEquals(EngineeringPlatformStatus.DOMAIN_FAILURE,
			executeValidate(command(1, "", "invalid manifest")).status());
		assertEquals(EngineeringPlatformStatus.USAGE_ERROR,
			executeValidate(command(2, "", "usage")).status());
	}

	@Test void mapsExecutionErrorAndResolveSuccess() {
		assertEquals(EngineeringPlatformStatus.EXECUTION_ERROR,
			executeValidate(command(-1, "", "cannot start")).status());
		when(executor.execute(any(com.aidevos.orchestrator.executor.command.CommandOptions.class)))
			.thenReturn(command(0, "Engineering Platform CLI 0.3.0", ""), command(0, "resolved", ""));
		assertEquals(EngineeringPlatformStatus.SUCCESS,
			adapter().resolve(root.resolve("project.yaml"), root, Duration.ofSeconds(1)).status());
	}

	@Test void recordsOnlyWorkspaceRelativeCommandMetadata() {
		EngineeringPlatformResult result = executeValidate(command(0, "ok", ""));
		assertEquals("project.yaml", result.commandMetadata().get("projectYaml"));
	}

	@Test void reportsDisabledMissingExecutableMissingRootAndVersionMismatch() throws Exception {
		properties.setEnabled(false);
		assertEquals("Engineering Platform integration is disabled", validate().stderr());
		properties.setEnabled(true);
		properties.setExecutable(root.resolve("missing").toString());
		assertEquals("Engineering Platform executable does not exist", validate().stderr());
		properties.setExecutable(Files.createFile(root.resolve("ep-two")).toString());
		properties.setPlatformRoot(root.resolve("missing-root").toString());
		assertEquals("Engineering Platform root does not exist", validate().stderr());
		properties.setPlatformRoot(root.toString());
		properties.setVersion("v9.0.0");
		assertEquals("Engineering Platform Trial requires version v0.3.0", validate().stderr());
	}

	@Test void reportsActualExecutableVersionMismatch() {
		when(executor.execute(any(com.aidevos.orchestrator.executor.command.CommandOptions.class)))
			.thenReturn(command(0, "Engineering Platform CLI 0.2.0", ""));
		assertEquals(EngineeringPlatformStatus.EXECUTION_ERROR, validate().status());
	}

	private EngineeringPlatformResult executeValidate(CommandResult operation) {
		when(executor.execute(any(com.aidevos.orchestrator.executor.command.CommandOptions.class)))
			.thenReturn(command(0, "Engineering Platform CLI 0.3.0", ""), operation);
		return validate();
	}

	private EngineeringPlatformResult validate() {
		return adapter().validate(root.resolve("project.yaml"), root, Duration.ofSeconds(1));
	}

	private CommandEngineeringPlatformAdapter adapter() {
		return new CommandEngineeringPlatformAdapter(executor, properties);
	}

	private CommandResult command(int exitCode, String output, String error) {
		CommandResult result = new CommandResult();
		result.setExitCode(exitCode); result.setSuccess(exitCode == 0);
		result.setOutput(output); result.setError(error);
		return result;
	}
}
