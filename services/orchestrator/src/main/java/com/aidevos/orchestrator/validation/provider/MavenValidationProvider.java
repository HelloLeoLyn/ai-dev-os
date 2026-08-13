package com.aidevos.orchestrator.validation.provider;

import java.util.List;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.validation.ValidationCheckType;
import org.springframework.stereotype.Component;

@Component
public class MavenValidationProvider extends CommandValidationSupport implements ValidationProvider {
	public MavenValidationProvider(CommandExecutor executor) { super(executor); }
	@Override public boolean supports(ValidationContext context) {
		return context.capabilities().containsKey("mavenDirectory")
			&& (context.type() == ValidationCheckType.BACKEND_TEST
				|| context.type() == ValidationCheckType.BACKEND_BUILD);
	}
	@Override public ValidationCheckResult execute(ValidationContext context) {
		String directory = context.capabilities().get("mavenDirectory").toString();
		List<String> command = context.type() == ValidationCheckType.BACKEND_TEST
			? List.of("mvn", "test") : List.of("mvn", "package", "-DskipTests");
		return run(command, directory);
	}
	@Override public String name() { return "maven"; }
}
