package com.aidevos.orchestrator.tool.mcp;

public class McpException extends RuntimeException {

	private final String code;

	public McpException(String code, String message) {
		super(message);
		this.code = code;
	}

	public McpException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
