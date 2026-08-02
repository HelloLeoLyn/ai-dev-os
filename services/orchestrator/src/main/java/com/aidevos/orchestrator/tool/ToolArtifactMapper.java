package com.aidevos.orchestrator.tool;

import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionArtifact;

public interface ToolArtifactMapper {

	List<ExecutionArtifact> map(ToolInvocation invocation, ToolResult result);
}
