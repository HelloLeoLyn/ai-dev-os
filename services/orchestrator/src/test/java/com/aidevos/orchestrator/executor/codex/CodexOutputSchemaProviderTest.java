package com.aidevos.orchestrator.executor.codex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexOutputSchemaProviderTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void projectAnalysisSchemaRequiresEveryPropertyAtEveryObjectNode() throws IOException {
		CodexOutputSchemaProvider provider = new CodexOutputSchemaProvider();
		provider.initialize();
		try {
			JsonNode schema = objectMapper.readTree(Files.readString(Path.of(provider.path(true))));

			assertStrictObjectSchemas(schema, "$");
		} finally {
			provider.cleanup();
		}
	}

	@Test
	void projectAnalysisEvidenceOptionalFieldsAreRequiredAndNullable() throws IOException {
		CodexOutputSchemaProvider provider = new CodexOutputSchemaProvider();
		provider.initialize();
		try {
			JsonNode schema = objectMapper.readTree(Files.readString(Path.of(provider.path(true))));
			JsonNode evidence = schema.path("$defs").path("evidence");

			for (String name : Set.of("label", "artifactType", "uri", "line", "contentHash")) {
				assertTrue(requiredNames(evidence).contains(name), name + " must be required");
				assertTrue(typeNames(evidence.path("properties").path(name)).contains("null"),
					name + " must remain nullable");
			}
		} finally {
			provider.cleanup();
		}
	}

	@Test
	void projectAnalysisEvidenceOnlyAllowsSourceFilesAndArtifacts() throws IOException {
		CodexOutputSchemaProvider provider=new CodexOutputSchemaProvider(); provider.initialize();
		try {
			JsonNode evidence=objectMapper.readTree(Files.readString(Path.of(provider.path(true))))
				.path("$defs").path("evidence");
			Set<String> allowed=new HashSet<>(); evidence.path("properties").path("type").path("enum")
				.forEach(value->allowed.add(value.asText()));
			assertEquals(Set.of("SOURCE_FILE","ARTIFACT"),allowed);
			assertTrue(evidence.path("properties").path("ref").path("description").asText()
				.contains("workspace-relative"));
		} finally { provider.cleanup(); }
	}

	@Test
	void projectAnalysisRequiresLocalRecommendationIdentityOnly() throws IOException {
		CodexOutputSchemaProvider provider=new CodexOutputSchemaProvider(); provider.initialize();
		try {
			JsonNode recommendation=objectMapper.readTree(Files.readString(Path.of(provider.path(true))))
				.path("properties").path("recommendations").path("items");
			assertTrue(recommendation.path("properties").has("localRecommendationId"));
			assertTrue(!recommendation.path("properties").has("recommendationId"));
		} finally { provider.cleanup(); }
	}

	private void assertStrictObjectSchemas(JsonNode node, String path) {
		if (isObjectSchema(node)) {
			JsonNode properties = node.get("properties");
			JsonNode required = node.get("required");
			assertNotNull(properties, path + " must define properties");
			assertTrue(properties.isObject(), path + ".properties must be an object");
			assertNotNull(required, path + " must define required");
			assertTrue(required.isArray(), path + ".required must be an array");
			assertEquals(new HashSet<>(properties.propertyNames()), requiredNames(node),
				path + " required keys must equal properties keys");
		}

		if (node.isObject()) {
			node.propertyNames().forEach(name ->
				assertStrictObjectSchemas(node.get(name), path + "." + name));
		} else if (node.isArray()) {
			for (int index = 0; index < node.size(); index++) {
				assertStrictObjectSchemas(node.get(index), path + "[" + index + "]");
			}
		}
	}

	private boolean isObjectSchema(JsonNode node) {
		return node.isObject() && typeNames(node).contains("object");
	}

	private Set<String> requiredNames(JsonNode objectSchema) {
		Set<String> names = new HashSet<>();
		JsonNode required = objectSchema.path("required");
		if (required.isArray()) {
			required.forEach(value -> names.add(value.asText()));
		}
		return names;
	}

	private Set<String> typeNames(JsonNode schema) {
		Set<String> names = new HashSet<>();
		JsonNode type = schema.path("type");
		if (type.isTextual()) {
			names.add(type.asText());
		} else if (type.isArray()) {
			type.forEach(value -> names.add(value.asText()));
		}
		return names;
	}
}
