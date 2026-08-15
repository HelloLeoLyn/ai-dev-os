package com.aidevos.orchestrator.operation;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.execution.ExecutionEngine;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeterministicExecutionRoutingTest {
    @Test
    void gitStatusDoesNotResolveOrCallCodex() throws Exception {
        CommandResult commandResult = new CommandResult(); commandResult.setSuccess(true); commandResult.setExitCode(0); commandResult.setOutput("clean");
        CommandExecutor command = new CommandExecutor() { @Override public CommandResult execute(CommandOptions options) { return commandResult; } };
        AgentResolver resolver = mock(AgentResolver.class);
        ExecutionRecordManager records = new ExecutionRecordManager();
        ExecutionEngine engine = new ExecutionEngine(resolver, records, com.aidevos.orchestrator.audit.AuditService.noop(),
            new com.aidevos.orchestrator.execution.InMemoryExecutionAttemptRepository(), new DeterministicOperationExecutor(command));
        TaskDefinition task = new TaskDefinition(); task.setId("task-deterministic"); task.setName("git status");
        task.setOperation(new OperationSpec("git.status", Map.of(), Duration.ofSeconds(5)));
        task.setMetadata(Map.of("workspacePath", Files.createTempDirectory("deterministic-routing").toString()));

        ExecutionResult result = engine.execute(task, "job-deterministic");

        assertTrue(result.isSuccess());
        verifyNoInteractions(resolver);
        ExecutionRecord record = records.getAll().getFirst();
        assertEquals("deterministic", record.getExecutorName());
        assertEquals("git.status", record.getOperation());
    }
}
