package com.aidevos.orchestrator.testfixture;

import java.nio.file.Path;

import com.aidevos.orchestrator.execution.workspace.CodingWorkspaceProperties;
import com.aidevos.orchestrator.execution.workspace.ExecutionWorkspaceService;
import com.aidevos.orchestrator.execution.workspace.InMemoryExecutionWorkspaceRepository;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.workspace.WorkspaceService;

/** Creates an isolated, task-scoped worktree service for integration tests. */
public final class ExecutionWorkspaceTestFixture {
    private ExecutionWorkspaceTestFixture() {
    }

    public static ExecutionWorkspaceService service(WorkspaceService sourceWorkspaces, Path testRoot) {
        CodingWorkspaceProperties properties = new CodingWorkspaceProperties();
        properties.setExecutionWorkspaceRoot(testRoot.resolve("execution-workspaces").toString());
        return new ExecutionWorkspaceService(new InMemoryExecutionWorkspaceRepository(),
                sourceWorkspaces, new CommandExecutor(), properties);
    }

}
