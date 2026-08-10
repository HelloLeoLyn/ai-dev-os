package com.aidevos.orchestrator.pr.provider;

import com.aidevos.orchestrator.pr.MockPullRequestProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that aidevos.git.provider selects the pull request provider:
 * mock by default, github or gitlab when configured, and a clear startup
 * failure when the selected provider misses its required credentials.
 */
class ProviderSelectionTest {

	@Configuration
	@Import({MockPullRequestProvider.class, GithubPullRequestProvider.class,
		GitLabPullRequestProvider.class})
	static class ProviderBeans {
	}

	@Test
	void shouldSelectMockByDefault() {
		new ApplicationContextRunner()
			.withUserConfiguration(ProviderBeans.class)
			.run(context -> assertEquals(MockPullRequestProvider.class,
				context.getBean(GitProvider.class).getClass()));
	}

	@Test
	void shouldSelectGithubWhenConfigured() {
		GitProviderProperties properties = githubProperties();
		new ApplicationContextRunner()
			.withUserConfiguration(ProviderBeans.class)
			.withPropertyValues("aidevos.git.provider=github")
			.withBean(GitProviderProperties.class, () -> properties)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.run(context -> assertEquals(GithubPullRequestProvider.class,
				context.getBean(GitProvider.class).getClass()));
	}

	@Test
	void shouldSelectGitlabWhenConfigured() {
		GitProviderProperties properties = new GitProviderProperties();
		properties.setProvider("gitlab");
		properties.setGitlabToken("token");
		properties.setGitlabProjectId("123");
		new ApplicationContextRunner()
			.withUserConfiguration(ProviderBeans.class)
			.withPropertyValues("aidevos.git.provider=gitlab")
			.withBean(GitProviderProperties.class, () -> properties)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.run(context -> assertEquals(GitLabPullRequestProvider.class,
				context.getBean(GitProvider.class).getClass()));
	}

	@Test
	void shouldFailStartupWhenGithubCredentialsMissing() {
		GitProviderProperties properties = new GitProviderProperties();
		properties.setProvider("github");
		new ApplicationContextRunner()
			.withUserConfiguration(ProviderBeans.class)
			.withPropertyValues("aidevos.git.provider=github")
			.withBean(GitProviderProperties.class, () -> properties)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.run(context -> assertNotNull(context.getStartupFailure(),
				"expected startup failure for missing GitHub credentials"));
	}

	private GitProviderProperties githubProperties() {
		GitProviderProperties properties = new GitProviderProperties();
		properties.setProvider("github");
		properties.setGithubToken("token");
		properties.setGithubOwner("owner");
		properties.setGithubRepo("repo");
		return properties;
	}
}
