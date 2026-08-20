package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.execution.tool.DeterministicTool;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FailureClassifierTest {

	private final FailureClassifier classifier = new FailureClassifier();

	@Test
	void successOrNullResultIsNotClassified() {
		CommandResult success = new CommandResult();
		success.setSuccess(true);
		assertNull(classifier.classify(DeterministicTool.GIT, success));
		assertNull(classifier.classify(DeterministicTool.GIT, (CommandResult) null));
	}

	@Test
	void gitConflictIsClassifiedAsGitConflict() {
		CommandResult result = result("error: your local changes would be overwritten by merge");
		assertEquals(FailureClass.GIT_CONFLICT, classifier.classify(DeterministicTool.GIT, result));
	}

	@Test
	void gitNetworkFailureIsClassifiedAsNetworkError() {
		CommandResult result = result("fatal: unable to access 'https://example.com/repo.git/'");
		assertEquals(FailureClass.NETWORK_ERROR, classifier.classify(DeterministicTool.GIT, result));
	}

	@Test
	void mavenTestFailureIsTestFailed() {
		CommandResult result = result("Tests run: 10, Failures: 2, Errors: 0");
		assertEquals(FailureClass.TEST_FAILED, classifier.classify(DeterministicTool.MAVEN, result));
	}

	@Test
	void mavenBuildFailureIsBuildFailed() {
		CommandResult result = result("[ERROR] BUILD FAILURE");
		assertEquals(FailureClass.BUILD_FAILED, classifier.classify(DeterministicTool.MAVEN, result));
	}

	@Test
	void npmBuildErrorIsBuildFailed() {
		CommandResult result = result("npm ERR! code ELIFECYCLE");
		assertEquals(FailureClass.BUILD_FAILED, classifier.classify(DeterministicTool.NPM, result));
	}

	@Test
	void credentialsAreClassifiedBeforeToolSpecificRules() {
		CommandResult result = result("fatal: could not read Username for 'https://github.com': terminal prompts disabled");
		assertEquals(FailureClass.CREDENTIAL_MISSING,
			classifier.classify(DeterministicTool.GIT, result));
	}

	@Test
	void healthCheckFailureIsHealthCheckFailed() {
		CommandResult result = result("500 Internal Server Error");
		assertEquals(FailureClass.HEALTH_CHECK_FAILED,
			classifier.classify(DeterministicTool.HTTP_HEALTH, result));
	}

	@Test
	void usageLimitAndUnknownThrowableClassification() {
		CommandResult result = result("quota exceeded: usage limit reached");
		assertEquals(FailureClass.USAGE_LIMIT, classifier.classify(DeterministicTool.MAVEN, result));

		assertEquals(FailureClass.NETWORK_ERROR, classifier.classify(DeterministicTool.GIT,
			new RuntimeException("Connection refused: connect")));
		assertEquals(FailureClass.UNKNOWN, classifier.classify(DeterministicTool.GIT,
			new IllegalStateException("odd failure")));
	}

	@Test
	void permanentFailureClassesAreNeverRetried() {
		for (FailureClass permanent : new FailureClass[] { FailureClass.CREDENTIAL_MISSING,
				FailureClass.MODEL_NOT_FOUND, FailureClass.PROVIDER_DISABLED,
				FailureClass.APPROVAL_REQUIRED, FailureClass.GIT_CONFLICT }) {
			assertEquals(false, FailureClassifier.isRetryable(permanent));
		}
	}

	@Test
	void transientFailureClassesAreRetried() {
		for (FailureClass transientClass : new FailureClass[] { FailureClass.NETWORK_ERROR,
				FailureClass.HEALTH_CHECK_FAILED, FailureClass.EXECUTOR_FAILED,
				FailureClass.USAGE_LIMIT, FailureClass.UNKNOWN }) {
			assertEquals(true, FailureClassifier.isRetryable(transientClass));
		}
		assertEquals(true, FailureClassifier.isRetryable(null));
	}

	private CommandResult result(String error) {
		CommandResult result = new CommandResult();
		result.setSuccess(false);
		result.setExitCode(1);
		result.setOutput("");
		result.setError(error);
		return result;
	}
}
