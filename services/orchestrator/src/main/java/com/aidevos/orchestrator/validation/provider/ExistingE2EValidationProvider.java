package com.aidevos.orchestrator.validation.provider;

import java.util.List;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import org.springframework.stereotype.Component;

@Component
public class ExistingE2EValidationProvider extends CommandValidationSupport implements ValidationProvider {
	public ExistingE2EValidationProvider(CommandExecutor executor) { super(executor); }
	@Override public boolean supports(ValidationContext context) {
		return context.type() == ValidationCheckType.E2E
			&& context.capabilities().containsKey("frontendDirectory");
	}
	@Override public ValidationCheckResult execute(ValidationContext context) {
		@SuppressWarnings("unchecked") List<String> scripts =
			(List<String>) context.capabilities().getOrDefault("scripts", List.of());
		if (!scripts.contains("e2e")) return ValidationCheckResult.skipped("No e2e script");
		String manager = context.capabilities().get("packageManager").toString();
		List<String> command = "pnpm".equals(manager)
			? List.of("pnpm", "--config.lockfile=false", "e2e")
			: List.of("npm", "run", "e2e");
		return run(command, context.capabilities().get("frontendDirectory").toString());
	}
	@Override public String name() { return "existing-e2e"; }
}
