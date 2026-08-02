package com.aidevos.orchestrator.execution;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArtifactContentLimiter {

	private final int maxContentLength;

	public ArtifactContentLimiter(
			@Value("${coding.artifacts.max-content-length:262144}") int maxContentLength) {
		if (maxContentLength < 1) {
			throw new IllegalArgumentException("Artifact max content length must be positive");
		}
		this.maxContentLength = maxContentLength;
	}

	public void apply(ExecutionArtifact artifact, String content) {
		String value = content == null ? "" : content;
		artifact.getMetadata().put("originalLength", value.length());
		if (value.length() > maxContentLength) {
			artifact.setContent(value.substring(0, maxContentLength));
			artifact.getMetadata().put("truncated", true);
			artifact.getMetadata().put("storedLength", maxContentLength);
		}
		else {
			artifact.setContent(value);
			artifact.getMetadata().put("truncated", false);
			artifact.getMetadata().put("storedLength", value.length());
		}
	}
}
