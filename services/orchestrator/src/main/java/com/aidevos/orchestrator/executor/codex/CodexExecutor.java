package com.aidevos.orchestrator.executor.codex;

import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.approval.CodingApprovalRequest;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.approval.ApprovalStatus;
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
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
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
	private final PlanApprovalService planApprovalService;

	public CodexExecutor(CommandExecutor commandExecutor, WorkspaceResolver workspaceResolver,
			GitInspector gitInspector, CodexResultMapper resultMapper,
			CodingApprovalService approvalService, ArtifactContentLimiter artifactContentLimiter,
			CodexProperties codexProperties, CodexCommandBuilder commandBuilder,
			UntrackedArtifactCollector untrackedArtifactCollector) {
		this(commandExecutor, workspaceResolver, gitInspector, resultMapper, approvalService,
			artifactContentLimiter, codexProperties, commandBuilder, untrackedArtifactCollector, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public CodexExecutor(CommandExecutor commandExecutor, WorkspaceResolver workspaceResolver,
			GitInspector gitInspector, CodexResultMapper resultMapper,
			CodingApprovalService approvalService, ArtifactContentLimiter artifactContentLimiter,
			CodexProperties codexProperties, CodexCommandBuilder commandBuilder,
			UntrackedArtifactCollector untrackedArtifactCollector, PlanApprovalService planApprovalService) {
		this.commandExecutor = commandExecutor;
		this.workspaceResolver = workspaceResolver;
		this.gitInspector = gitInspector;
		this.resultMapper = resultMapper;
		this.approvalService = approvalService;
		this.artifactContentLimiter = artifactContentLimiter;
		this.codexProperties = codexProperties;
		this.commandBuilder = commandBuilder;
		this.untrackedArtifactCollector = untrackedArtifactCollector;
		this.planApprovalService = planApprovalService;
	}

	@Override
	public String getType() {
		return "codex";
	}

	@Override
	public ExecutionResult execute(ExecutionContext context) {
		WorkspaceSnapshot workspace = workspaceResolver.resolve(context);
		CodexSandbox sandbox = readOnly(context) ? CodexSandbox.READ_ONLY
			: CodexSandbox.parse(codingConfig(context, "sandbox", "workspace-write"));
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
		ApprovedExecutionHandoff handoff = approvedExecutionHandoff(context, workspace.path(), sandbox);
		if (!readOnly(context) && planApprovalService != null && handoff == null) {
			throw new IllegalStateException("Approved execution handoff could not be verified");
		}
		GitSnapshot before = gitInspector.capture(workspace.path());
		CommandOptions options = new CommandOptions();
		options.setCommand(commandBuilder.build(context, workspace.path(), sandbox, handoff));
		options.setWorkingDirectory(workspace.path());
		options.setTimeout(codexProperties.getTimeout());
		CommandResult commandResult = commandExecutor.execute(options);
		GitSnapshot after = gitInspector.capture(workspace.path());
		CodexOutput codexOutput = resultMapper.map(commandResult.getOutput());
		CodexExecutionResult executionResult = CodexExecutionResult.of(commandResult.isSuccess(),
			commandResult.getExitCode(), commandResult.getOutput(), commandResult.getError(),
			workspace.path(), after.branch().trim(), after.status(), after.diffStat());
		return toExecutionResult(executionResult, codexOutput, sandbox, before, after,
			contextMetadataString(context, "approvalId"), projectAnalysis(context));
	}

	private ApprovedExecutionHandoff approvedExecutionHandoff(ExecutionContext context,
			String executionWorkspace, CodexSandbox sandbox) {
		if (readOnly(context) || sandbox != CodexSandbox.WORKSPACE_WRITE || planApprovalService == null) {
			return null;
		}
		String planApprovalId = metadataString(context, "planApprovalId");
		String planId = metadataString(context, "planId");
		String planVersionText = metadataString(context, "planVersion");
		String approvalId = metadataString(context, "approvalId");
		if (blank(planApprovalId) || blank(planId) || blank(planVersionText) || blank(approvalId)) return null;
		int planVersion;
		try { planVersion = Integer.parseInt(planVersionText); }
		catch (NumberFormatException exception) { return null; }
		PlanApprovalRequest planApproval = planApprovalService.get(planApprovalId);
		if (planApproval == null || (planApproval.getStatus() != ApprovalStatus.APPROVED
				&& planApproval.getStatus() != ApprovalStatus.CONSUMED)
				|| !planId.equals(planApproval.getPlanId()) || planVersion != planApproval.getPlanVersion()) return null;
		CodingApprovalRequest codingApproval = approvalService.get(approvalId);
		if (codingApproval == null || codingApproval.getStatus() != ApprovalStatus.CONSUMED
				|| !context.getTaskId().equals(codingApproval.getTaskId())
				|| !context.getJobId().equals(codingApproval.getJobId())
				|| !executionWorkspace.equals(codingApproval.getWorkspace())
				|| !"CODING".equals(codingApproval.getAuthority())
				|| !"WORKSPACE_WRITE".equals(codingApproval.getOperation())) return null;
		return new ApprovedExecutionHandoff(context.getTaskId(), planId, planVersion, context.getJobId(),
			executionWorkspace, approvalId, codingApproval.getAuthority(), codingApproval.getOperation());
	}

	private String metadataString(ExecutionContext context, String key) {
		Object value = context.getMetadata().get(key);
		return value == null ? null : String.valueOf(value);
	}

	private boolean blank(String value) { return value == null || value.isBlank(); }

	private boolean readOnly(ExecutionContext context) {
		Object value = context.getParameters().get("executionMode");
		return value != null && "READ_ONLY".equalsIgnoreCase(String.valueOf(value));
	}

	private ExecutionResult toExecutionResult(CodexExecutionResult executionResult,
			CodexOutput codexOutput, CodexSandbox sandbox, GitSnapshot before, GitSnapshot after,
			String approvalId, boolean projectAnalysis) {
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
		if (executionResult.success() && projectAnalysis && codexOutput.structuredPayload() != null) {
			ExecutionArtifact analysis = textArtifact("analysis-result", "analysis-result.json",
				codexOutput.structuredPayload());
			analysis.setMediaType("application/json");
			result.getArtifacts().add(analysis);
		}
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

	private boolean projectAnalysis(ExecutionContext context) {
		Object value = context.getParameters().get("taskType");
		return "project-analysis".equals(value);
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
