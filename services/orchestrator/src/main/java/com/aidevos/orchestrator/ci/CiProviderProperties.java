package com.aidevos.orchestrator.ci;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CI provider configuration: aidevos.ci.provider selects mock (default),
 * github (GitHub Actions) or gitlab (GitLab CI). Credentials are reused from
 * the aidevos.git.* properties (GITHUB_TOKEN / GITLAB_TOKEN environment
 * variables); nothing is hardcoded.
 */
@Component
@ConfigurationProperties(prefix = "aidevos.ci")
public class CiProviderProperties {

	private String provider = "mock";

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}
}
