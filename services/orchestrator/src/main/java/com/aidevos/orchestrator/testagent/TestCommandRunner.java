package com.aidevos.orchestrator.testagent;

/**
 * Abstraction for executing test commands. Kept intentionally independent from
 * OpenClaw / executors so tests can run through a local process or a future
 * sandboxed runner without changing the TestAgentService.
 */
public interface TestCommandRunner {

	TestCommandResult run(String command, String workdir);
}
