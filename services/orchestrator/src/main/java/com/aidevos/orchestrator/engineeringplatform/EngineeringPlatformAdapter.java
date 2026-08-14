package com.aidevos.orchestrator.engineeringplatform;

import java.nio.file.Path;
import java.time.Duration;

public interface EngineeringPlatformAdapter {

	EngineeringPlatformResult validate(Path projectYaml, Path workspace, Duration timeout);

	EngineeringPlatformResult resolve(Path projectYaml, Path workspace, Duration timeout);

	EngineeringPlatformResult generate(Path projectYaml, Path output, Path workspace,
		Duration timeout);

	EngineeringPlatformResult conformance(Path projectYaml, Path projectDirectory,
		Path workspace, Duration timeout);
}
