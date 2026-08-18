package com.aidevos.orchestrator.remote;

/**
 * Optional remote name for a push action. When absent the push uses origin.
 */
public record RemotePushRequest(String remote, String approvalId) {
	public RemotePushRequest(String remote) { this(remote, null); }
}
