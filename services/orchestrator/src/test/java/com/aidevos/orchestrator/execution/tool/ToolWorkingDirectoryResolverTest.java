package com.aidevos.orchestrator.execution.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolWorkingDirectoryResolverTest {

	@TempDir
	Path workspace;

	@Test
	void mavenStepResolvesToModuleDirectoryContainingPomXml() throws IOException {
		Files.createDirectories(workspace.resolve("services/orchestrator"));
		Files.writeString(workspace.resolve("services/orchestrator/pom.xml"), "<project/>");

		String resolved = ToolWorkingDirectoryResolver.resolve(DeterministicTool.MAVEN,
			workspace.toString());

		assertEquals(workspace.resolve("services/orchestrator").toString(), resolved);
		assertTrue(Path.of(resolved).startsWith(workspace));
	}
}
