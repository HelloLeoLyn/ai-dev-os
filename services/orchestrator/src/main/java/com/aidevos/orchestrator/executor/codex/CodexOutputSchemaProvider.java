package com.aidevos.orchestrator.executor.codex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class CodexOutputSchemaProvider {

	private Path schemaPath;

	@PostConstruct
	void initialize() throws IOException {
		schemaPath = Files.createTempFile("ai-dev-os-codex-result-", ".json");
		Files.writeString(schemaPath, """
			{
			  "type": "object",
			  "additionalProperties": false,
			  "required": ["summary", "changedFiles", "tests", "risks"],
			  "properties": {
			    "summary": {"type": "string"},
			    "changedFiles": {"type": "array", "items": {"type": "string"}},
			    "tests": {"type": "array", "items": {"type": "string"}},
			    "risks": {"type": "array", "items": {"type": "string"}}
			  }
			}
			""");
	}

	public String path() {
		if (schemaPath == null) {
			throw new IllegalStateException("Codex output schema is not initialized");
		}
		return schemaPath.toString();
	}

	@PreDestroy
	void cleanup() throws IOException {
		if (schemaPath != null) {
			Files.deleteIfExists(schemaPath);
		}
	}
}
