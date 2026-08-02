package com.aidevos.orchestrator.executor.command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.time.Duration;

import com.aidevos.orchestrator.executor.command.approval.ApprovalGate;
import com.aidevos.orchestrator.executor.command.approval.ApprovalRequest;
import com.aidevos.orchestrator.executor.command.policy.CommandPolicyProperties;
import com.aidevos.orchestrator.executor.command.policy.ConfigurableCommandPolicy;
import com.aidevos.orchestrator.executor.command.policy.PolicyAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandExecutorTest {

	private final CommandExecutor commandExecutor = new CommandExecutor();

	@TempDir
	Path temporaryDirectory;

	@Test
	void shouldExecuteCommandSuccessfully() {
		CommandResult result = commandExecutor.execute(List.of("echo", "hello"));

		assertTrue(result.isSuccess());
		assertEquals(0, result.getExitCode());
		assertTrue(result.getOutput().contains("hello"));
	}

	@Test
	void shouldReturnFailureForInvalidCommand() {
		CommandResult result = commandExecutor.execute(List.of("command-that-does-not-exist"));

		assertFalse(result.isSuccess());
		assertEquals(-1, result.getExitCode());
		assertFalse(result.getError().isBlank());
	}

	@Test
	void shouldTerminateCommandAfterTimeout() {
		CommandOptions options = new CommandOptions();
		options.setCommand(List.of("sh", "-c", "sleep 5 & wait"));
		options.setTimeout(Duration.ofMillis(20));
		long startedAt = System.nanoTime();

		CommandResult result = commandExecutor.execute(options);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

		assertFalse(result.isSuccess());
		assertEquals(-1, result.getExitCode());
		assertEquals("Command timed out after PT0.02S", result.getError());
		assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0);
	}

	@Test
	void shouldCloseChildStandardInput() {
		CommandOptions options = new CommandOptions();
		options.setCommand(List.of("sh", "-c", "cat >/dev/null; echo stdin-closed"));
		options.setTimeout(Duration.ofSeconds(1));

		CommandResult result = commandExecutor.execute(options);

		assertTrue(result.isSuccess());
		assertEquals("stdin-closed", result.getOutput().trim());
	}

	@Test
	void shouldExecuteCommandInConfiguredWorkingDirectory() throws IOException {
		CommandOptions options = new CommandOptions();
		options.setCommand(List.of("pwd"));
		options.setWorkingDirectory(temporaryDirectory.toString());

		CommandResult result = commandExecutor.execute(options);

		assertTrue(result.isSuccess());
		assertEquals(temporaryDirectory.toRealPath().toString(), result.getOutput().trim());
	}

	@Test
	void shouldPreserveExistingBehaviorWhenPolicyIsDisabled() {
		CommandPolicyProperties properties = new CommandPolicyProperties();
		properties.setEnabled(false);
		CommandExecutor policyAwareExecutor = commandExecutor(properties);

		CommandResult result = policyAwareExecutor.execute(List.of("echo", "compatible"));

		assertTrue(result.isSuccess());
		assertTrue(result.getOutput().contains("compatible"));
	}

	@Test
	void shouldExecuteCommandAllowedByPolicy() {
		CommandPolicyProperties properties = propertiesWithRule("allow-echo", "echo",
			List.of("allowed"), PolicyAction.ALLOW);
		CommandExecutor policyAwareExecutor = commandExecutor(properties);

		CommandResult result = policyAwareExecutor.execute(List.of("echo", "allowed"));

		assertTrue(result.isSuccess());
		assertEquals(0, result.getExitCode());
		assertTrue(result.getOutput().contains("allowed"));
	}

	@Test
	void shouldDenyCommandBeforeStartingProcess() {
		CommandPolicyProperties properties = propertiesWithRule("deny-missing-command",
			"command-that-does-not-exist", List.of(), PolicyAction.DENY);
		CommandExecutor policyAwareExecutor = commandExecutor(properties);

		CommandResult result = policyAwareExecutor.execute(List.of("command-that-does-not-exist"));

		assertFalse(result.isSuccess());
		assertEquals(-1, result.getExitCode());
		assertEquals("Command denied by policy rule: deny-missing-command", result.getError());
	}

	@Test
	void shouldRequireApprovalWithoutStartingProcess() {
		CommandPolicyProperties properties = propertiesWithRule("approve-missing-command",
			"command-that-does-not-exist", List.of(), PolicyAction.REQUIRE_APPROVAL);
		CommandExecutor policyAwareExecutor = commandExecutor(properties);

		CommandResult result = policyAwareExecutor.execute(List.of("command-that-does-not-exist"));

		assertFalse(result.isSuccess());
		assertEquals(-1, result.getExitCode());
		assertEquals("APPROVAL_REQUIRED", result.getError());
	}

	@Test
	void shouldExecuteCommandAfterApproval() {
		CommandPolicyProperties properties = propertiesWithRule("approve-echo", "echo",
			List.of("approved"), PolicyAction.REQUIRE_APPROVAL);
		ApprovalGate approvalGate = new ApprovalGate();
		ApprovalRequest request = new ApprovalRequest(List.of("echo", "approved"), null, "approve-echo");
		approvalGate.approve(request);
		CommandExecutor policyAwareExecutor = new CommandExecutor(
			new ConfigurableCommandPolicy(properties), approvalGate);

		CommandResult result = policyAwareExecutor.execute(List.of("echo", "approved"));

		assertTrue(result.isSuccess());
		assertEquals(0, result.getExitCode());
		assertTrue(result.getOutput().contains("approved"));
	}

	@Test
	void shouldAllowExistingGitAndCodexCommandShapes() {
		CommandPolicyProperties properties = new CommandPolicyProperties();
		properties.setEnabled(true);
		properties.setDefaultAction(PolicyAction.DENY);
		properties.setRules(List.of(
			rule("allow-git-status", "git", List.of("status", "--short"), PolicyAction.ALLOW),
			rule("allow-git-diff", "git", List.of("diff", "--stat"), PolicyAction.ALLOW),
			rule("allow-codex-exec", "codex", List.of("exec"), PolicyAction.ALLOW),
			rule("allow-codex-noninteractive", "codex",
				List.of("--ask-for-approval", "never", "exec"), PolicyAction.ALLOW)));
		ConfigurableCommandPolicy policy = new ConfigurableCommandPolicy(properties);

		assertTrue(policy.evaluate(options(List.of("git", "status", "--short"))).isAllowed());
		assertTrue(policy.evaluate(options(List.of("git", "diff", "--stat"))).isAllowed());
		assertTrue(policy.evaluate(options(List.of("codex", "exec", "task description"))).isAllowed());
		assertTrue(policy.evaluate(options(List.of("codex", "--ask-for-approval", "never", "exec",
			"task description"))).isAllowed());
	}

	private CommandExecutor commandExecutor(CommandPolicyProperties properties) {
		return new CommandExecutor(new ConfigurableCommandPolicy(properties));
	}

	private CommandPolicyProperties propertiesWithRule(String id, String executable,
			List<String> argumentPrefix, PolicyAction action) {
		CommandPolicyProperties properties = new CommandPolicyProperties();
		properties.setEnabled(true);
		properties.setDefaultAction(PolicyAction.DENY);
		properties.setRules(List.of(rule(id, executable, argumentPrefix, action)));
		return properties;
	}

	private CommandPolicyProperties.Rule rule(String id, String executable,
			List<String> argumentPrefix, PolicyAction action) {
		CommandPolicyProperties.Rule rule = new CommandPolicyProperties.Rule();
		rule.setId(id);
		rule.setExecutable(executable);
		rule.setArgumentPrefix(argumentPrefix);
		rule.setAction(action);
		return rule;
	}

	private CommandOptions options(List<String> command) {
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		return options;
	}
}
