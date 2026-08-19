package com.aidevos.orchestrator.executor.codex;

/**
 * Structured outcome parsed from the codex CLI JSONL stream. failureMessage is
 * the real turn.failed error when the CLI reports one; the fallback path (CLI
 * stderr) is handled by the executor only when this is null.
 */
public record CodexOutput(String threadId, String summary, String structuredPayload,
		String failureMessage, String failureType) {

	public CodexOutput(String threadId, String summary, String structuredPayload) {
		this(threadId, summary, structuredPayload, null, null);
	}
}
