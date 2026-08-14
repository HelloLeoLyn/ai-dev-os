package com.aidevos.orchestrator.executor.command;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aidevos.orchestrator.executor.command.approval.ApprovalDecision;
import com.aidevos.orchestrator.executor.command.approval.ApprovalGate;
import com.aidevos.orchestrator.executor.command.approval.ApprovalRequest;
import com.aidevos.orchestrator.executor.command.policy.CommandPolicy;
import com.aidevos.orchestrator.executor.command.policy.PolicyDecision;
import com.aidevos.orchestrator.executor.command.policy.PolicyAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.aidevos.orchestrator.network.ProxyEnvironmentService;

@Component
public class CommandExecutor {

	private final CommandPolicy commandPolicy;
	private final ApprovalGate approvalGate;
	private final ProxyEnvironmentService proxyEnvironmentService;

	public CommandExecutor() {
		this(options -> PolicyDecision.allow("policy-disabled"), new ApprovalGate(), null);
	}

	public CommandExecutor(CommandPolicy commandPolicy) {
		this(commandPolicy, new ApprovalGate(), null);
	}

	public CommandExecutor(CommandPolicy commandPolicy, ApprovalGate approvalGate) {
		this(commandPolicy, approvalGate, null);
	}

	@Autowired
	public CommandExecutor(CommandPolicy commandPolicy, ApprovalGate approvalGate,
			ProxyEnvironmentService proxyEnvironmentService) {
		this.commandPolicy = commandPolicy;
		this.approvalGate = approvalGate;
		this.proxyEnvironmentService = proxyEnvironmentService;
	}

	public CommandResult execute(List<String> command) {
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		return execute(options);
	}

	public CommandResult execute(CommandOptions options) {
		PolicyDecision decision = commandPolicy.evaluate(options);
		if (decision.action() == PolicyAction.DENY) {
			return result(false, -1, "", "Command denied by policy rule: " + decision.ruleId());
		}
		if (decision.action() == PolicyAction.REQUIRE_APPROVAL) {
			ApprovalRequest request = approvalRequest(options, decision);
			if (approvalGate.evaluate(request) != ApprovalDecision.APPROVED) {
				return result(false, -1, "", "APPROVAL_REQUIRED");
			}
		}

		Process process = null;
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(options.getCommand());
			if (options.isRuntimeNetworkEnabled() && proxyEnvironmentService != null) {
				proxyEnvironmentService.applyTo(processBuilder.environment());
			}
			for (Map.Entry<String, String> entry : options.getEnvironment().entrySet()) {
				if (entry.getValue() == null) processBuilder.environment().remove(entry.getKey());
				else processBuilder.environment().put(entry.getKey(), entry.getValue());
			}
			if (options.getWorkingDirectory() != null && !options.getWorkingDirectory().isBlank()) {
				processBuilder.directory(new File(options.getWorkingDirectory()));
			}
			process = processBuilder.start();
			Process runningProcess = process;
			closeInput(runningProcess.getOutputStream());

			try (ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
				Future<String> output = streamExecutor.submit(() -> read(runningProcess.getInputStream()));
				Future<String> error = streamExecutor.submit(() -> read(runningProcess.getErrorStream()));
				if (options.getTimeout() != null && !runningProcess.waitFor(
						options.getTimeout().toNanos(), TimeUnit.NANOSECONDS)) {
					runningProcess.descendants().forEach(ProcessHandle::destroyForcibly);
					runningProcess.destroyForcibly();
					runningProcess.waitFor();
					return result(false, -1, "", "Command timed out after " + options.getTimeout());
				}
				if (options.getTimeout() == null) {
					runningProcess.waitFor();
				}
				int exitCode = runningProcess.exitValue();

				return result(exitCode == 0, exitCode, output.get(), error.get());
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			if (process != null) {
				process.destroyForcibly();
			}
			return result(false, -1, "", ex.getMessage());
		}
		catch (IOException | ExecutionException ex) {
			return result(false, -1, "", ex.getMessage());
		}
	}

	private ApprovalRequest approvalRequest(CommandOptions options, PolicyDecision decision) {
		return new ApprovalRequest(options.getCommand(), options.getWorkingDirectory(), decision.ruleId());
	}

	private String read(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

	private void closeInput(OutputStream input) throws IOException {
		input.close();
	}

	private CommandResult result(boolean success, int exitCode, String output, String error) {
		CommandResult result = new CommandResult();
		result.setSuccess(success);
		result.setExitCode(exitCode);
		result.setOutput(output);
		result.setError(error);
		return result;
	}
}
