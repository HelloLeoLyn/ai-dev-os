package com.aidevos.orchestrator.tool;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import org.springframework.stereotype.Component;

@Component
public class DefaultToolArtifactMapper implements ToolArtifactMapper {

	private final ArtifactContentLimiter contentLimiter;

	public DefaultToolArtifactMapper(ArtifactContentLimiter contentLimiter) {
		this.contentLimiter = contentLimiter;
	}

	@Override
	public List<ExecutionArtifact> map(ToolInvocation invocation, ToolResult result) {
		List<ExecutionArtifact> artifacts = new ArrayList<>();
		for (ToolContent item : result.content()) {
			ExecutionArtifact artifact = new ExecutionArtifact();
			artifact.setType(item.type());
			artifact.setName(item.name());
			artifact.setMediaType(item.mediaType());
			artifact.setUri(item.uri());
			contentLimiter.apply(artifact, item.content());
			artifact.getMetadata().putAll(item.metadata());
			artifact.getMetadata().put("executionId", invocation.executionId());
			artifact.getMetadata().put("invocationId", invocation.invocationId());
			artifact.getMetadata().put("providerId", invocation.providerId());
			artifact.getMetadata().put("toolName", invocation.toolName());
			artifacts.add(artifact);
		}
		return List.copyOf(artifacts);
	}
}
