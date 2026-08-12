package com.aidevos.orchestrator.orchestration;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.mcp.tool.McpToolRouter;
import com.aidevos.orchestrator.mcp.tool.ToolDefinition;
import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.planner.PlanningResult;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.ExecutionMode;

/**
 * Per-node execution context: the task, the graph/node being executed, the
 * workspace to operate in and the input prompt. A pre-computed PlanningResult
 * is carried through so the HERMES node reuses it instead of planning twice.
 */
public class AgentExecutionContext {

	private String taskId;
	private TaskRecord task;
	private String workspaceId;
	private String workspacePath;
	private ExecutionMode executionMode = ExecutionMode.READ_WRITE;
	private String graphId;
	private String nodeId;
	private AgentType agentType;
	private String input;
	private PlanningResult planningResult;
	private MemoryContext memoryHints;
	private List<ToolDefinition> availableTools = List.of();
	private McpToolRouter toolRouter;

	public String getTaskId() {
		return taskId;
	}

	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}

	public TaskRecord getTask() {
		return task;
	}

	public void setTask(TaskRecord task) {
		this.task = task;
	}

	public String getWorkspaceId() {
		return workspaceId;
	}

	public void setWorkspaceId(String workspaceId) {
		this.workspaceId = workspaceId;
	}

	public String getWorkspacePath() {
		return workspacePath;
	}

	public void setWorkspacePath(String workspacePath) {
		this.workspacePath = workspacePath;
	}

	public ExecutionMode getExecutionMode() {
		return executionMode;
	}

	public void setExecutionMode(ExecutionMode executionMode) {
		this.executionMode = executionMode == null ? ExecutionMode.READ_WRITE : executionMode;
	}

	public String getGraphId() {
		return graphId;
	}

	public void setGraphId(String graphId) {
		this.graphId = graphId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public AgentType getAgentType() {
		return agentType;
	}

	public void setAgentType(AgentType agentType) {
		this.agentType = agentType;
	}

	public String getInput() {
		return input;
	}

	public void setInput(String input) {
		this.input = input;
	}

	public PlanningResult getPlanningResult() {
		return planningResult;
	}

	public void setPlanningResult(PlanningResult planningResult) {
		this.planningResult = planningResult;
	}

	public MemoryContext getMemoryHints() {
		return memoryHints;
	}

	public void setMemoryHints(MemoryContext memoryHints) {
		this.memoryHints = memoryHints;
	}

	public List<ToolDefinition> getAvailableTools() {
		return availableTools;
	}

	public void setAvailableTools(List<ToolDefinition> availableTools) {
		this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
	}

	public McpToolRouter getToolRouter() {
		return toolRouter;
	}

	public void setToolRouter(McpToolRouter toolRouter) {
		this.toolRouter = toolRouter;
	}
}
