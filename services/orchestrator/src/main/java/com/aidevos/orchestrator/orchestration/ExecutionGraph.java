package com.aidevos.orchestrator.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Execution graph for one task: the nodes (each with an agent type and
 * dependencies) and the downstream edges. A repair graph additionally
 * declares a bounded loop (loopStart/loopEnd/maxAttempts) so a failed
 * TEST_AGENT_VERIFY re-enters REPAIR_AGENT_ANALYZE without an unbounded
 * retry; maxAttempts defaults to the existing RepairPolicy bound.
 */
public class ExecutionGraph {

	private final String graphId;
	private final String taskId;
	private final Map<String, ExecutionNode> nodes = new LinkedHashMap<>();
	private final Map<String, List<String>> edges = new LinkedHashMap<>();
	private final String loopStartNodeId;
	private final String loopEndNodeId;
	private final int maxAttempts;

	public ExecutionGraph(String graphId, String taskId, List<ExecutionNode> nodes,
			String loopStartNodeId, String loopEndNodeId, int maxAttempts) {
		this.graphId = graphId;
		this.taskId = taskId;
		if (nodes != null) {
			for (ExecutionNode node : nodes) {
				this.nodes.put(node.getNodeId(), node);
			}
		}
		for (ExecutionNode node : this.nodes.values()) {
			this.edges.put(node.getNodeId(), new ArrayList<>());
			for (String dependency : node.getDependencies()) {
				this.edges.computeIfAbsent(dependency, ignored -> new ArrayList<>())
					.add(node.getNodeId());
			}
		}
		this.loopStartNodeId = loopStartNodeId == null ? "" : loopStartNodeId;
		this.loopEndNodeId = loopEndNodeId == null ? "" : loopEndNodeId;
		this.maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
	}

	public ExecutionNode getNode(String nodeId) {
		return nodes.get(nodeId);
	}

	public List<ExecutionNode> getNodes() {
		return List.copyOf(nodes.values());
	}

	/** nodeId -> downstream node ids. */
	public Map<String, List<String>> getEdges() {
		Map<String, List<String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : edges.entrySet()) {
			copy.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return copy;
	}

	public List<String> getTopologicalOrder() {
		Map<String, Integer> inDegree = new LinkedHashMap<>();
		for (ExecutionNode node : nodes.values()) {
			inDegree.put(node.getNodeId(), node.getDependencies().size());
		}
		List<String> order = new ArrayList<>();
		while (order.size() < nodes.size()) {
			String ready = null;
			for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
				if (entry.getValue() == 0 && !order.contains(entry.getKey())) {
					ready = entry.getKey();
					break;
				}
			}
			if (ready == null) {
				throw new IllegalStateException("Execution graph contains a cycle");
			}
			order.add(ready);
			for (String downstream : edges.getOrDefault(ready, List.of())) {
				inDegree.computeIfPresent(downstream, (key, value) -> value - 1);
			}
		}
		return order;
	}

	/** Resets the loop section (loopStart..loopEnd) back to PENDING for retry. */
	public void resetLoop() {
		if (loopStartNodeId.isBlank()) {
			return;
		}
		boolean inside = false;
		for (ExecutionNode node : nodes.values()) {
			if (node.getNodeId().equals(loopStartNodeId)) {
				inside = true;
			}
			if (inside) {
				node.reset();
			}
			if (node.getNodeId().equals(loopEndNodeId)) {
				break;
			}
		}
	}

	public String getGraphId() {
		return graphId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getLoopStartNodeId() {
		return loopStartNodeId;
	}

	public String getLoopEndNodeId() {
		return loopEndNodeId;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public boolean hasLoop() {
		return !loopStartNodeId.isBlank() && !loopEndNodeId.isBlank();
	}
}
