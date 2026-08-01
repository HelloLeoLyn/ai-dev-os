package com.aidevos.orchestrator.executor.command.policy;

import com.aidevos.orchestrator.executor.command.CommandOptions;

public interface CommandPolicy {

	PolicyDecision evaluate(CommandOptions options);
}
