package com.aidevos.orchestrator.executor.codex;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.approval.CodingApprovalRequest;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.execution.workspace.WorkspaceSnapshot;
import com.aidevos.orchestrator.executor.AgentExecutor;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.GitSnapshot;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import org.springframework.stereotype.Component;

/**
 * AgentExecutor that drives the real codex CLI for coding tasks. Resolves and
 * validates the workspace, requests write approval for workspace-write
 * sandboxes, captures a git snapshot before and after the run and returns a
 * structured ExecutionResult enriched with git state and codex metadata. The
 * CLI command itself is assembled by CodexCommandBuilder.
 */
@Component
public class CodexExecutor implements AgentExecutor {

	private final CommandExecutor commandExecutor;
	private final WorkspaceResolver workspaceResolver;
	private final GitInspector gitInspector;
	private final CodexResultMapper resultMapper;
	private final CodingApprovalService approvalService;
	private final ArtifactContentLimiter artifactContentLimiter;
	private final CodexProperties codexProperties;
	private final CodexCommandBuilder commandBuilder;
	private final UntrackedArtifactCollector untrackedArtifactCollector;

	public CodexExecutor(CommandExecutor commandExecutor, WorkspaceResolver workspaceResolver,
			GitInspector gitInspector, CodexResultMapper resultMapper,
			CodingApprovalService approvalService, ArtifactContentLimiter artifactContentLimiter,
			CodexProperties codexProperties, CodexCommandBuilder commandBuilder,
			UntrackedArtifactCollector untrackedArtifactCollector) {
		this.commandExecutor = commandExecutor;
		this.workspaceResolver = workspaceResolver;
		this.gitInspector = gitInspector;
		this.resultMapper = resultMapper;
		this.approvalService = approvalService;
		this.artifactContentLimiter = artifactContentLimiter;
		this.codexProperties = codexProperties;
		this.commandBuilder = commandBuilder;
		this.untrackedArtifactCollector = untrackedArtifactCollector;
	}

	@Override
	public String getType() {
		return "codex";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		WorkspaceSnapshot workspace = workspaceResolver.resolve(context);
		CodexSandbox sandbox = CodexSandbox.parse(codingConfig(context, "sandbox", "workspace-write"));
		CodingApprovalRequest approval = approvalService.requireApproval(context, workspace.path(), sandbox);
		if (approval != null) {
			ExecutionResult waiting = new ExecutionResult();
			waiting.setSuccess(false);
			waiting.setApprovalRequired(true);
			waiting.setApprovalId(approval.getId());
			waiting.setMessage("APPROVAL_REQUIRED");
			waiting.getMetadata().put("workspace", workspace.path());
			waiting.getMetadata().put("sandbox", sandbox.cliValue());
			return waiting;
		}
		GitSnapshot before = gitInspector.capture(workspace.path());
		CommandOptions options = new CommandOptions();
		options.setCommand(commandBuilder.build(context, workspace.path(), sandbox));
		options.setWorkingDirectory(workspace.path());
		options.setTimeout(codexProperties.getTimeout());
		CommandResult commandResult = commandExecutor.execute(options);
		GitSnapshot after = gitInspector.capture(workspace.path());
		CodexOutput codexOutput = resultMapper.map(commandResult.getOutput());
		CodexExecutionResult executionResult = CodexExecutionResult.of(commandResult.isSuccess(),
			commandResult.getExitCode(), commandResult.getOutput(), commandResult.getError(),
			workspace.path(), after.branch().trim(), after.status(), after.diffStat());
		return toExecutionResult(executionResult, codexOutput, sandbox, before, after,
			contextMetadataString(context, "approvalId"));
	}

	private ExecutionResult toExecutionResult(CodexExecutionResult executionResult,
			CodexOutput codexOutput, CodexSandbox sandbox, GitSnapshot before, GitSnapshot after,
			String approvalId) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(executionResult.success());
		if (executionResult.success()) {
			result.setMessage("Task executed successfully");
			result.setOutput(codexOutput.summary() == null
				? executionResult.stdout() : codexOutput.summary());
		}
		else {
			result.setMessage(executionResult.stderr());
		}
		result.getArtifacts().add(textArtifact("git-status-before", "git-status-before.txt", before.status()));
		result.getArtifacts().add(textArtifact("git-status-after", "git-status-after.txt", after.status()));
		result.getArtifacts().add(textArtifact("git-diff-stat", "git-diff-stat.txt", after.diffStat()));
		result.getArtifacts().add(textArtifact("git-diff", "changes.patch", after.patch()));
		result.getArtifacts().add(textArtifact("git-cached-diff", "cached-changes.patch", after.cachedDiff()));
		result.getArtifacts().addAll(untrackedArtifactCollector.collect(executionResult.workspace(),
			newUntrackedFiles(before, after)));
		result.getArtifacts().add(textArtifact("codex-events", "codex-events.jsonl",
			executionResult.stdout()));
		ExecutionArtifact summary = textArtifact("codex-result", "codex-result.txt",
			result.getOutput());
		summary.getMetadata().put("threadId", codexOutput.threadId());
		summary.getMetadata().put("workspace", executionResult.workspace());
		summary.getMetadata().put("sandbox", sandbox.cliValue());
		summary.getMetadata().put("branch", after.branch().trim());
		summary.getMetadata().put("beforeHead", before.head().trim());
		summary.getMetadata().put("afterHead", after.head().trim());
		summary.getMetadata().put("exitCode", executionResult.exitCode());
		result.getArtifacts().add(summary);
		result.getMetadata().put("workspace", executionResult.workspace());
		result.getMetadata().put("sandbox", sandbox.cliValue());
		result.getMetadata().put("branch", after.branch().trim());
		result.getMetadata().put("beforeHead", before.head().trim());
		result.getMetadata().put("afterHead", after.head().trim());
		result.getMetadata().put("exitCode", executionResult.exitCode());
		result.getMetadata().put("codexThreadId", codexOutput.threadId());
		result.setApprovalId(approvalId);
		return result;
	}

	private ExecutionArtifact textArtifact(String type, String name, String content) {
		ExecutionArtifact artifact = new ExecutionArtifact();
		artifact.setType(type);
		artifact.setName(name);
		artifact.setMediaType("text/plain");
		artifactContentLimiter.apply(artifact, content);
		return artifact;
	}

	private String codingConfig(ExecutionContext context, String key, String defaultValue) {
		Object coding = context.getParameters().get("coding");
		if (coding instanceof Map<?, ?> values) {
			Object value = values.get(key);
			if (value instanceof String text && !text.isBlank()) {
				return text;
			}
		}
		Object value = context.getParameters().get(key);
		return value instanceof String string && !string.isBlank() ? string : defaultValue;
	}

	private String contextMetadataString(ExecutionContext context, String key) {
		Object value = context.getMetadata().get(key);
		return value instanceof String text ? text : null;
	}

	private List<String> newUntrackedFiles(GitSnapshot before, GitSnapshot after) {
		return after.untrackedFiles().stream()
			.filter(path -> !before.untrackedFiles().contains(path))
			.toList();
	}
}
