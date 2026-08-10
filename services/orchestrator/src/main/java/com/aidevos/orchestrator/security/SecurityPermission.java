package com.aidevos.orchestrator.security;

/**
 * Granular security permissions an agent policy can allow, deny or require
 * approval for. Tool types map to these permissions in the MCP router.
 */
public enum SecurityPermission {
	READ_FILE,
	WRITE_FILE,
	EXECUTE_COMMAND,
	GIT_WRITE,
	NETWORK_ACCESS,
	SECRET_ACCESS,
	BROWSER_EXECUTE
}
