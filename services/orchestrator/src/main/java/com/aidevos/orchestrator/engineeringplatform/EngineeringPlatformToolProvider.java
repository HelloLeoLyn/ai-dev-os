package com.aidevos.orchestrator.engineeringplatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.tool.ToolAccess;
import com.aidevos.orchestrator.tool.ToolContent;
import com.aidevos.orchestrator.tool.ToolDefinition;
import com.aidevos.orchestrator.tool.ToolInvocation;
import com.aidevos.orchestrator.tool.ToolProvider;
import com.aidevos.orchestrator.tool.ToolResult;
import org.springframework.stereotype.Component;

@Component
public class EngineeringPlatformToolProvider implements ToolProvider {

	public static final String ID = "engineering-platform";
	private final EngineeringPlatformAdapter adapter;
	private final EngineeringPlatformProperties properties;

	public EngineeringPlatformToolProvider(EngineeringPlatformAdapter adapter,
			EngineeringPlatformProperties properties) {
		this.adapter = adapter;
		this.properties = properties;
	}

	@Override
	public String getId() { return ID; }

	@Override
	public List<ToolDefinition> getTools() {
		Map<String, Object> manifestSchema = Map.of("projectYaml", "String");
		return List.of(
			new ToolDefinition(ID, "validate", "Validate an Engineering Platform project manifest",
				manifestSchema, ToolAccess.READ_ONLY),
			new ToolDefinition(ID, "resolve", "Resolve an Engineering Platform project manifest",
				manifestSchema, ToolAccess.READ_ONLY),
			new ToolDefinition(ID, "generate", "Generate into the bound task workspace",
				Map.of("projectYaml", "String", "outputPath", "String"), ToolAccess.WORKSPACE_WRITE));
	}

	@Override
	public ToolResult invoke(ToolInvocation invocation) {
		try {
			Path workspace = requireWorkspace(invocation.workspace());
			Path manifest = existingFile(workspace, required(invocation, "projectYaml"));
			EngineeringPlatformResult result = switch (invocation.toolName()) {
				case "validate" -> adapter.validate(manifest, workspace, invocation.timeout());
				case "resolve" -> adapter.resolve(manifest, workspace, invocation.timeout());
				case "generate" -> adapter.generate(manifest,
					outputPath(workspace, required(invocation, "outputPath")), workspace,
					invocation.timeout());
				default -> null;
			};
			if (result == null) return ToolResult.failure("EP_OPERATION_NOT_ALLOWED",
				"Engineering Platform operation is not exposed");
			return toolResult(result);
		}
		catch (IllegalArgumentException | IOException exception) {
			return ToolResult.failure("EP_SCOPE_DENIED", exception.getMessage());
		}
	}

	private ToolResult toolResult(EngineeringPlatformResult result) {
		boolean success = result.status() == EngineeringPlatformStatus.SUCCESS;
		String output = result.stdout() == null || result.stdout().isBlank()
			? result.stderr() : result.stdout();
		String code = "EP_" + result.status().name();
		String message = success ? "Engineering Platform operation succeeded"
			: diagnostic(result);
		return new ToolResult(null, null, success, code, message, output,
			List.of(ToolContent.text("engineering-platform.txt", output)),
			result.commandMetadata());
	}

	private String diagnostic(EngineeringPlatformResult result) {
		if (result.stderr() != null && !result.stderr().isBlank()) return result.stderr();
		if (result.stdout() != null && !result.stdout().isBlank()) return result.stdout();
		return "Engineering Platform operation failed with exit " + result.exitCode();
	}

	private Path requireWorkspace(String value) throws IOException {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("A bound task workspace is required");
		}
		Path workspace = Path.of(value).toAbsolutePath().normalize();
		if (!Files.isDirectory(workspace)) throw new IllegalArgumentException("Workspace does not exist");
		return workspace.toRealPath();
	}

	private Path existingFile(Path workspace, String value) throws IOException {
		Path candidate = resolve(workspace, value);
		if (!Files.isRegularFile(candidate)) throw new IllegalArgumentException("project.yaml does not exist");
		Path real = candidate.toRealPath();
		if (!real.startsWith(workspace)) throw new IllegalArgumentException("project.yaml is outside workspace");
		return real;
	}

	private Path outputPath(Path workspace, String value) throws IOException {
		Path candidate = resolve(workspace, value);
		if (properties.getPlatformRoot() != null && !properties.getPlatformRoot().isBlank()) {
			Path platformRoot = Path.of(properties.getPlatformRoot()).toAbsolutePath().normalize();
			if (candidate.startsWith(platformRoot)) {
				throw new IllegalArgumentException("Output path cannot write Engineering Platform root");
			}
		}
		Path existing = candidate;
		while (existing != null && !Files.exists(existing)) existing = existing.getParent();
		if (existing == null || !existing.toRealPath().startsWith(workspace)) {
			throw new IllegalArgumentException("Output path is outside workspace");
		}
		return candidate;
	}

	private Path resolve(Path workspace, String value) {
		Path supplied = Path.of(value);
		Path candidate = (supplied.isAbsolute() ? supplied : workspace.resolve(supplied))
			.toAbsolutePath().normalize();
		if (!candidate.startsWith(workspace)) throw new IllegalArgumentException("Path is outside workspace");
		return candidate;
	}

	private String required(ToolInvocation invocation, String name) {
		Object value = invocation.arguments().get(name);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return text;
	}
}
