package com.aidevos.orchestrator.execution.workspace;

import java.util.List;

public interface ExecutionWorkspaceRepository {
    void save(ExecutionWorkspace workspace);
    ExecutionWorkspace findByTaskId(String taskId);
    ExecutionWorkspace get(String id);
    List<ExecutionWorkspace> getAll();
}
