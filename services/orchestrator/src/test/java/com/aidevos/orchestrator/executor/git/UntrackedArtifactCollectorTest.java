package com.aidevos.orchestrator.executor.git;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UntrackedArtifactCollectorTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void shouldCollectTextAndBinaryFilesWithLimitsWithoutFollowingOutsideSymlink() throws Exception {
		Path workspace = Files.createDirectory(temporaryDirectory.resolve("workspace"));
		Files.writeString(workspace.resolve("new.txt"), "abcdef", StandardCharsets.UTF_8);
		Files.write(workspace.resolve("binary.dat"), new byte[] { 1, 0, 2 });
		Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "secret");
		Files.createSymbolicLink(workspace.resolve("outside-link.txt"), outside);
		UntrackedArtifactCollector collector = new UntrackedArtifactCollector(
			new ArtifactContentLimiter(100), 5);

		List<ExecutionArtifact> artifacts = collector.collect(workspace.toString(),
			List.of("new.txt", "binary.dat", "outside-link.txt"));

		assertEquals(3, artifacts.size());
		ExecutionArtifact index = artifacts.get(0);
		assertEquals("git-untracked-files", index.getType());
		assertEquals(3, index.getMetadata().get("count"));
		assertEquals(List.of("outside-link.txt"), index.getMetadata().get("skippedPaths"));

		ExecutionArtifact text = artifacts.get(1);
		assertEquals("git-untracked-file", text.getType());
		assertEquals("abcde", text.getContent());
		assertEquals(true, text.getMetadata().get("truncatedBytes"));
		assertEquals(false, text.getMetadata().get("binary"));

		ExecutionArtifact binary = artifacts.get(2);
		assertEquals("application/octet-stream", binary.getMediaType());
		assertNull(binary.getContent());
		assertEquals(true, binary.getMetadata().get("binary"));
		assertFalse(Files.exists(workspace.resolve("unexpected")));
		assertTrue(Files.exists(outside));
	}
}
