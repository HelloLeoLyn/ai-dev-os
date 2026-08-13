package com.aidevos.orchestrator.validation.provider;

import java.util.List;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import org.springframework.stereotype.Component;

@Component
public class FrontendValidationProvider extends CommandValidationSupport implements ValidationProvider {
	public FrontendValidationProvider(CommandExecutor executor) { super(executor); }
	@Override public boolean supports(ValidationContext context) {
		return context.capabilities().containsKey("frontendDirectory")
			&& (context.type() == ValidationCheckType.FRONTEND_TEST
				|| context.type() == ValidationCheckType.FRONTEND_BUILD);
	}
	@Override public ValidationCheckResult execute(ValidationContext context) {
		String directory = context.capabilities().get("frontendDirectory").toString();
		String manager = context.capabilities().get("packageManager").toString();
		String script = context.type() == ValidationCheckType.FRONTEND_TEST ? "test" : "build";
		@SuppressWarnings("unchecked") List<String> scripts =
			(List<String>) context.capabilities().getOrDefault("scripts", List.of());
		if (!scripts.contains(script)) return ValidationCheckResult.skipped("No " + script + " script");
		List<String> command = "pnpm".equals(manager)
			? List.of("pnpm", "--config.lockfile=false", script)
			: List.of("npm", "run", script);
		return run(command, directory);
	}
	@Override public String name() { return "frontend"; }
}
