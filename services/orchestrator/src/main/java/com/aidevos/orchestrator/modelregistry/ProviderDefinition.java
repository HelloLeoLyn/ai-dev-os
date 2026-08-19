package com.aidevos.orchestrator.modelregistry;

/**
 * Runtime-configurable model provider. Only the credential reference name is
 * stored; the actual secret value is never persisted, returned by the API or
 * written to audit logs.
 */
public class ProviderDefinition {

	private String providerId;
	private String displayName;
	private String baseUrl;
	private String credentialRef;
	private boolean enabled = true;

	public ProviderDefinition() {
	}

	public String getProviderId() {
		return providerId;
	}

	public void setProviderId(String providerId) {
		this.providerId = providerId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getCredentialRef() {
		return credentialRef;
	}

	public void setCredentialRef(String credentialRef) {
		this.credentialRef = credentialRef;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
