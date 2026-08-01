package com.aidevos.orchestrator.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionArtifact {

	private String type;
	private String name;
	private String mediaType;
	private String uri;
	private String content;
	private Map<String, Object> metadata = new LinkedHashMap<>();

	public ExecutionArtifact() {
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMediaType() {
		return mediaType;
	}

	public void setMediaType(String mediaType) {
		this.mediaType = mediaType;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
}
