package com.aidevos.orchestrator.operation;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DeterministicOperationExecutor {
    private final CommandExecutor commandExecutor;

    public DeterministicOperationExecutor(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public ExecutionResult execute(OperationSpec spec, ExecutionContext context) {
        String workspace = context.getWorkspace();
        if (workspace == null || workspace.isBlank()) return failure("WORKSPACE_REQUIRED", "Trusted workspace is required");
        return switch (spec.operation()) {
            case "fs.exists" -> exists(spec, workspace);
            case "git.status" -> command(spec, workspace, List.of("git", "status", "--short"));
            case "git.diff" -> command(spec, workspace, List.of("git", "diff", "--stat"));
            case "git.diff_check" -> command(spec, workspace, List.of("git", "diff", "--check"));
            case "git.log" -> command(spec, workspace, List.of("git", "log", "-n", "20", "--oneline"));
            case "maven.compile" -> command(spec, workspace, List.of("mvn", "-DskipTests", "compile"));
            case "maven.test" -> command(spec, workspace, mavenTest(spec));
            case "npm.test" -> command(spec, workspace, List.of("npm", "test"));
            case "npm.build" -> command(spec, workspace, List.of("npm", "run", "build"));
            default -> failure("UNSUPPORTED_OPERATION", "Unsupported deterministic operation: " + spec.operation());
        };
    }

    private List<String> mavenTest(OperationSpec spec) {
        List<String> command = new ArrayList<>(List.of("mvn"));
        Object tests = spec.args().get("tests");
        if (tests != null) {
            if (!(tests instanceof List<?> values) || values.isEmpty()
                    || values.stream().anyMatch(value -> !(value instanceof String text) || !text.matches("[A-Za-z0-9_.$]+"))) {
                throw new IllegalArgumentException("maven.test args.tests must contain valid test class names");
            }
            command.add("-Dtest=" + values.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElseThrow());
        }
        command.add("test");
        return command;
    }

    private ExecutionResult exists(OperationSpec spec, String workspace) {
        Object value = spec.args().get("path");
        if (!(value instanceof String relative) || relative.isBlank()) return failure("PATH_REQUIRED", "fs.exists args.path is required");
        Path root = Path.of(workspace).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !target.startsWith(root)) return failure("WORKSPACE_BOUNDARY", "Path escapes workspace");
        ExecutionResult result = new ExecutionResult();
        result.setSuccess(Files.exists(target));
        result.setMessage(Boolean.toString(Files.exists(target)));
        result.getMetadata().put("operation", spec.operation());
        result.getMetadata().put("workspace", workspace);
        result.getMetadata().put("exitCode", Files.exists(target) ? 0 : 1);
        return result;
    }

    private ExecutionResult command(OperationSpec spec, String workspace, List<String> command) {
        CommandOptions options = new CommandOptions();
        options.setCommand(command);
        options.setWorkingDirectory(workspace);
        options.setTimeout(spec.timeout());
        CommandResult result = commandExecutor.execute(options);
        ExecutionResult execution = new ExecutionResult();
        execution.setSuccess(result.isSuccess());
        execution.setOutput(result.getOutput());
        execution.setMessage(result.getError());
        execution.getMetadata().put("operation", spec.operation());
        execution.getMetadata().put("workspace", workspace);
        execution.getMetadata().put("exitCode", result.getExitCode());
        return execution;
    }

    private ExecutionResult failure(String code, String message) {
        ExecutionResult result = new ExecutionResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.getMetadata().put("errorCode", code);
        return result;
    }
}
