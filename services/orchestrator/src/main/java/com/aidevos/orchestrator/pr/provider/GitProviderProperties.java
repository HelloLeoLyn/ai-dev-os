package com.aidevos.orchestrator.pr.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Remote git provider configuration. Tokens are never hardcoded: GitHub and
 * GitLab credentials default from the GITHUB_* / GITLAB_* environment
 * variables and can be overridden through the aidevos.git.* properties.
 * provider selects mock (default), github or gitlab.
 */
@Component
@ConfigurationProperties(prefix = "aidevos.git")
public class GitProviderProperties {

	private String provider = "mock";

	private String githubToken = env("GITHUB_TOKEN");
	private String githubOwner = env("GITHUB_OWNER");
	private String githubRepo = env("GITHUB_REPO");
	private String githubBaseUrl = "https://api.github.com";

	private String gitlabToken = env("GITLAB_TOKEN");
	private String gitlabProjectId = env("GITLAB_PROJECT_ID");
	private String gitlabBaseUrl = "https://gitlab.com/api/v4";

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getGithubToken() {
		return githubToken;
	}

	public void setGithubToken(String githubToken) {
		this.githubToken = githubToken;
	}

	public String getGithubOwner() {
		return githubOwner;
	}

	public void setGithubOwner(String githubOwner) {
		this.githubOwner = githubOwner;
	}

	public String getGithubRepo() {
		return githubRepo;
	}

	public void setGithubRepo(String githubRepo) {
		this.githubRepo = githubRepo;
	}

	public String getGithubBaseUrl() {
		return githubBaseUrl;
	}

	public void setGithubBaseUrl(String githubBaseUrl) {
		this.githubBaseUrl = githubBaseUrl;
	}

	public String getGitlabToken() {
		return gitlabToken;
	}

	public void setGitlabToken(String gitlabToken) {
		this.gitlabToken = gitlabToken;
	}

	public String getGitlabProjectId() {
		return gitlabProjectId;
	}

	public void setGitlabProjectId(String gitlabProjectId) {
		this.gitlabProjectId = gitlabProjectId;
	}

	public String getGitlabBaseUrl() {
		return gitlabBaseUrl;
	}

	public void setGitlabBaseUrl(String gitlabBaseUrl) {
		this.gitlabBaseUrl = gitlabBaseUrl;
	}

	private static String env(String name) {
		String value = System.getenv(name);
		return value == null ? "" : value;
	}
}
