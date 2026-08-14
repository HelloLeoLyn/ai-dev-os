package com.aidevos.orchestrator.validation.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectCapabilityDetector {
	private final ObjectMapper mapper;

	public ProjectCapabilityDetector(ObjectMapper mapper) { this.mapper = mapper; }

	public Map<String, Object> detect(Path workspace) {
		Map<String, Object> result = new LinkedHashMap<>();
		Path maven = firstFile(workspace, List.of("pom.xml", "server/pom.xml", "backend/pom.xml"));
		Path frontend = packageDirectory(workspace);
		Path engineeringPlatformManifest = firstFile(workspace, List.of("project.yaml"));
		if (maven != null) result.put("mavenDirectory", maven.getParent().toString());
		if (engineeringPlatformManifest != null) {
			result.put("engineeringPlatformProjectYaml", engineeringPlatformManifest.toString());
		}
		if (frontend != null) {
			result.put("frontendDirectory", frontend.toString());
			result.put("packageManager", packageManager(frontend));
			result.put("scripts", scripts(frontend.resolve("package.json")));
		}
		return Map.copyOf(result);
	}

	private Path packageDirectory(Path root) {
		for (String candidate : List.of("frontend/package.json", "package.json")) {
			Path file = root.resolve(candidate).normalize();
			if (Files.isRegularFile(file)) return file.getParent();
		}
		return null;
	}

	private Path firstFile(Path root, List<String> candidates) {
		for (String candidate : candidates) {
			Path file = root.resolve(candidate).normalize();
			if (Files.isRegularFile(file)) return file;
		}
		return null;
	}

	private String packageManager(Path directory) {
		if (Files.isRegularFile(directory.resolve("pnpm-lock.yaml"))) return "pnpm";
		if (Files.isRegularFile(directory.resolve("package-lock.json"))) return "npm";
		return "npm";
	}

	private List<String> scripts(Path packageJson) {
		try {
			JsonNode node = mapper.readTree(Files.readString(packageJson)).path("scripts");
			if (!node.isObject()) return List.of();
			java.util.ArrayList<String> names = new java.util.ArrayList<>();
			names.addAll(node.propertyNames());
			return List.copyOf(names);
		}
		catch (IOException exception) { return List.of(); }
	}
}
