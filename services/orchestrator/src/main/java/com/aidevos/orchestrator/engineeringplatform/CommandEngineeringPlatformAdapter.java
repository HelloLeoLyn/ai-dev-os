package com.aidevos.orchestrator.engineeringplatform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.springframework.stereotype.Component;

@Component
public class CommandEngineeringPlatformAdapter implements EngineeringPlatformAdapter {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);
	private final CommandExecutor executor;
	private final EngineeringPlatformProperties properties;

	public CommandEngineeringPlatformAdapter(CommandExecutor executor,
			EngineeringPlatformProperties properties) {
		this.executor = executor;
		this.properties = properties;
	}

	@Override
	public EngineeringPlatformResult validate(Path projectYaml, Path workspace, Duration timeout) {
		return execute(EngineeringPlatformOperation.VALIDATE, workspace, timeout,
			List.of("validate", path(projectYaml)), metadata(workspace, projectYaml, null, null));
	}

	@Override
	public EngineeringPlatformResult resolve(Path projectYaml, Path workspace, Duration timeout) {
		return execute(EngineeringPlatformOperation.RESOLVE, workspace, timeout,
			List.of("resolve", path(projectYaml)), metadata(workspace, projectYaml, null, null));
	}

	@Override
	public EngineeringPlatformResult generate(Path projectYaml, Path output, Path workspace,
			Duration timeout) {
		return execute(EngineeringPlatformOperation.GENERATE, workspace, timeout,
			List.of("generate", path(projectYaml), "--output", path(output)),
			metadata(workspace, projectYaml, output, null));
	}

	@Override
	public EngineeringPlatformResult conformance(Path projectYaml, Path projectDirectory,
			Path workspace, Duration timeout) {
		return execute(EngineeringPlatformOperation.CONFORMANCE, workspace, timeout,
			List.of("conformance", path(projectYaml), path(projectDirectory)),
			metadata(workspace, projectYaml, null, projectDirectory));
	}

	private EngineeringPlatformResult execute(EngineeringPlatformOperation operation, Path workspace,
			Duration timeout, List<String> arguments, Map<String, Object> metadata) {
		Instant started = Instant.now();
		EngineeringPlatformResult unavailable = validateRuntime(operation, started, metadata);
		if (unavailable != null) return unavailable;
		EngineeringPlatformResult incompatible = validateVersion(operation, workspace, timeout,
			started, metadata);
		if (incompatible != null) return incompatible;

		List<String> command = new ArrayList<>();
		command.add(executable());
		command.addAll(arguments);
		CommandOptions options = new CommandOptions();
		options.setCommand(List.copyOf(command));
		options.setWorkingDirectory(workspace.toAbsolutePath().normalize().toString());
		options.setTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout);
		CommandResult result = executor.execute(options);
		long duration = Duration.between(started, Instant.now()).toMillis();
		Map<String, Object> completed = new LinkedHashMap<>(metadata);
		completed.put("operation", operation.name());
		completed.put("exitCode", result.getExitCode());
		completed.put("durationMs", duration);
		return new EngineeringPlatformResult(operation, result.getExitCode(), status(result),
			result.getOutput(), result.getError(), duration, completed);
	}

	private EngineeringPlatformResult validateVersion(EngineeringPlatformOperation operation,
			Path workspace, Duration timeout, Instant started, Map<String, Object> metadata) {
		CommandOptions options = new CommandOptions();
		options.setCommand(List.of(executable(), "--version"));
		options.setWorkingDirectory(workspace.toAbsolutePath().normalize().toString());
		options.setTimeout(timeout == null ? DEFAULT_TIMEOUT : timeout);
		CommandResult version = executor.execute(options);
		String expected = properties.getVersion().startsWith("v")
			? properties.getVersion().substring(1) : properties.getVersion();
		if (version.getExitCode() == 0 && version.getOutput() != null
				&& version.getOutput().contains(expected)) return null;
		String error = version.getExitCode() == 0
			? "Engineering Platform version does not match required " + properties.getVersion()
			: "Engineering Platform version check failed: " + version.getError();
		long duration = Duration.between(started, Instant.now()).toMillis();
		Map<String, Object> failed = new LinkedHashMap<>(metadata);
		failed.put("operation", operation.name());
		failed.put("exitCode", version.getExitCode());
		failed.put("durationMs", duration);
		return new EngineeringPlatformResult(operation, version.getExitCode(),
			EngineeringPlatformStatus.EXECUTION_ERROR, version.getOutput(), error, duration, failed);
	}

	private EngineeringPlatformResult validateRuntime(EngineeringPlatformOperation operation,
			Instant started, Map<String, Object> metadata) {
		String error = null;
		if (!properties.isEnabled()) error = "Engineering Platform integration is disabled";
		else if (properties.getExecutable() == null || properties.getExecutable().isBlank()
				|| !Files.isRegularFile(Path.of(properties.getExecutable()).toAbsolutePath().normalize())) {
			error = "Engineering Platform executable does not exist";
		}
		else if (properties.getPlatformRoot() == null || properties.getPlatformRoot().isBlank()
				|| !Files.isDirectory(Path.of(properties.getPlatformRoot()).toAbsolutePath().normalize())) {
			error = "Engineering Platform root does not exist";
		}
		else if (!"v0.3.0".equals(properties.getVersion())) {
			error = "Engineering Platform Trial requires version v0.3.0";
		}
		if (error == null) return null;
		long duration = Duration.between(started, Instant.now()).toMillis();
		Map<String, Object> failed = new LinkedHashMap<>(metadata);
		failed.put("operation", operation.name());
		failed.put("exitCode", -1);
		failed.put("durationMs", duration);
		return new EngineeringPlatformResult(operation, -1,
			EngineeringPlatformStatus.EXECUTION_ERROR, "", error, duration, failed);
	}

	private EngineeringPlatformStatus status(CommandResult result) {
		return switch (result.getExitCode()) {
			case 0 -> EngineeringPlatformStatus.SUCCESS;
			case 1 -> EngineeringPlatformStatus.DOMAIN_FAILURE;
			case 2 -> EngineeringPlatformStatus.USAGE_ERROR;
			default -> EngineeringPlatformStatus.EXECUTION_ERROR;
		};
	}

	private Map<String, Object> metadata(Path workspace, Path projectYaml, Path output,
			Path projectDirectory) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("projectYaml", relative(workspace, projectYaml));
		if (output != null) result.put("outputPath", relative(workspace, output));
		if (projectDirectory != null) result.put("projectDir", relative(workspace, projectDirectory));
		return result;
	}

	private String relative(Path workspace, Path value) {
		Path root = workspace.toAbsolutePath().normalize();
		Path target = value.toAbsolutePath().normalize();
		return target.startsWith(root) ? root.relativize(target).toString() : "<outside-workspace>";
	}

	private String path(Path value) {
		return value.toAbsolutePath().normalize().toString();
	}

	private String executable() {
		return Path.of(properties.getExecutable()).toAbsolutePath().normalize().toString();
	}
}
