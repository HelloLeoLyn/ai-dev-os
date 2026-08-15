package com.aidevos.orchestrator.operation;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicOperationExecutorTest {
    @Test
    void buildsAllowlistedCommandsWithoutShell() throws Exception {
        CommandResult expected = new CommandResult(); expected.setSuccess(true); expected.setExitCode(0); expected.setOutput("ok");
        CapturingCommandExecutor command = new CapturingCommandExecutor(expected);
        DeterministicOperationExecutor executor = new DeterministicOperationExecutor(command);
        ExecutionContext context = context(Files.createTempDirectory("deterministic-test"));

        var result = executor.execute(new OperationSpec("git.diff_check", Map.of(), Duration.ofSeconds(3)), context);

        assertTrue(result.isSuccess());
        assertEquals(List.of("git", "diff", "--check"), command.options.getCommand());
        assertFalse(command.options.getCommand().contains("sh"));
        assertEquals("git.diff_check", result.getMetadata().get("operation"));
    }

    @Test
    void preservesNonZeroExitAndOutput() throws Exception {
        CommandResult expected = new CommandResult(); expected.setSuccess(false); expected.setExitCode(2); expected.setOutput("stdout"); expected.setError("stderr");
        DeterministicOperationExecutor executor = new DeterministicOperationExecutor(new CapturingCommandExecutor(expected));
        var result = executor.execute(new OperationSpec("maven.test", Map.of("tests", List.of("A_Test")), Duration.ofSeconds(3)), context(Files.createTempDirectory("deterministic-test")));
        assertFalse(result.isSuccess()); assertEquals(2, result.getMetadata().get("exitCode"));
        assertEquals("stdout", result.getOutput()); assertEquals("stderr", result.getMessage());
    }

    @Test
    void rejectsWorkspaceEscapeAndUnsupportedOperation() throws Exception {
        DeterministicOperationExecutor executor = new DeterministicOperationExecutor(new CapturingCommandExecutor(new CommandResult()));
        ExecutionContext context = context(Files.createTempDirectory("deterministic-test"));
        assertFalse(executor.execute(new OperationSpec("fs.exists", Map.of("path", "../outside"), Duration.ofSeconds(1)), context).isSuccess());
        assertFalse(executor.execute(new OperationSpec("shell.execute", Map.of(), Duration.ofSeconds(1)), context).isSuccess());
    }

    private ExecutionContext context(Path workspace) { ExecutionContext context = new ExecutionContext(); context.setWorkspace(workspace.toString()); return context; }

    private static final class CapturingCommandExecutor extends CommandExecutor {
        private final CommandResult result; private CommandOptions options;
        private CapturingCommandExecutor(CommandResult result) { this.result = result; }
        @Override public CommandResult execute(CommandOptions options) { this.options = options; return result; }
    }
}
