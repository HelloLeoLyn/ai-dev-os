package com.aidevos.orchestrator.executor.codex;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "coding.codex")
public class CodexProperties {

	private String executable = "codex";
	private CodexApprovalPolicy approvalPolicy = CodexApprovalPolicy.NEVER;
	private Duration timeout = Duration.ofMinutes(10);

	public String getExecutable() { return executable; }
	public void setExecutable(String executable) { this.executable = executable; }
	public CodexApprovalPolicy getApprovalPolicy() { return approvalPolicy; }
	public void setApprovalPolicy(CodexApprovalPolicy approvalPolicy) { this.approvalPolicy = approvalPolicy; }
	public Duration getTimeout() { return timeout; }
	public void setTimeout(Duration timeout) { this.timeout = timeout; }
}
