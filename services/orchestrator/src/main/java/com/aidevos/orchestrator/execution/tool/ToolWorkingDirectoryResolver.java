package com.aidevos.orchestrator.execution.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the working directory for deterministic tool steps (maven/npm)
 * to the actual module directory inside the workspace boundary instead of
 * defaulting to the repository root when the step declares no explicit
 * working directory. Resolution never escapes the workspace: the result is
 * always a normalized path inside the workspace, otherwise the workspace
 * root itself is returned unchanged.
 */
public final class ToolWorkingDirectoryResolver {

	private static final int MAX_MODULE_DEPTH = 4;
	private static final List<String> SKIPPED_DIRECTORIES =
		List.of(".git", "node_modules", "target", "build", ".idea", ".venv", "dist");

	private ToolWorkingDirectoryResolver() {
	}

	public static String resolve(DeterministicTool tool, String workspacePath) {
		if (workspacePath == null || workspacePath.isBlank()) {
			return workspacePath;
		}
		Path root = Path.of(workspacePath).toAbsolutePath().normalize();
		if (!Files.isDirectory(root)) {
			return workspacePath;
		}
		String marker = switch (tool) {
			case MAVEN -> "pom.xml";
			case NPM -> "package.json";
			default -> null;
		};
		if (marker == null) {
			return workspacePath;
		}
		if (Files.isRegularFile(root.resolve(marker))) {
			return root.toString();
		}
		Path module = findModuleDirectory(root, marker);
		if (module == null) {
			return workspacePath;
		}
		Path resolved = module.toAbsolutePath().normalize();
		return resolved.startsWith(root) ? resolved.toString() : workspacePath;
	}

	/**
	 * Bounded, deterministic search for the shallowest directory containing
	 * the marker file. Skips build/vendor directories; returns null when the
	 * workspace cannot be read or no marker is found.
	 */
	private static Path findModuleDirectory(Path root, String marker) {
		try (Stream<Path> stream = Files.walk(root, MAX_MODULE_DEPTH)) {
			return stream
				.filter(path -> !isSkipped(root, path))
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().equals(marker))
				.map(Path::getParent)
				.sorted(Comparator.comparingInt(Path::getNameCount)
					.thenComparing(Path::toString))
				.findFirst().orElse(null);
		}
		catch (IOException | RuntimeException exception) {
			return null;
		}
	}

	private static boolean isSkipped(Path root, Path path) {
		for (Path segment : root.relativize(path)) {
			if (SKIPPED_DIRECTORIES.contains(segment.toString())) {
				return true;
			}
		}
		return false;
	}
}
