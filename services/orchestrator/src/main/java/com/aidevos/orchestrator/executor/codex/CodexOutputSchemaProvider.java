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
	private Path analysisSchemaPath;

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
		analysisSchemaPath = Files.createTempFile("ai-dev-os-analysis-result-", ".json");
		Files.writeString(analysisSchemaPath, """
			{
			  "type":"object","additionalProperties":false,
			  "required":["schemaVersion","summary","findings","recommendations"],
			  "properties":{
			    "schemaVersion":{"type":"string"},"summary":{"type":"string"},
			    "findings":{"type":"array","items":{"type":"object","additionalProperties":false,
			      "required":["findingId","title","summary","category","severity","confidence","scope","evidenceRefs"],
			      "properties":{"findingId":{"type":"string"},"title":{"type":"string"},"summary":{"type":"string"},
			      "category":{"type":"string"},"severity":{"enum":["LOW","MEDIUM","HIGH","CRITICAL"]},
			      "confidence":{"type":"number","minimum":0,"maximum":1},"scope":{"type":"array","items":{"type":"string"}},
			      "evidenceRefs":{"type":"array","items":{"$ref":"#/$defs/evidence"}}}}},
			    "recommendations":{"type":"array","items":{"type":"object","additionalProperties":false,
			      "required":["recommendationId","findingIds","title","rationale","priority","risk","benefit","scope","dependencies","suggestedExecutionMode","approvalRequired","evidenceRefs","confidence","recommendedNextAction"],
			      "properties":{"recommendationId":{"type":"string"},"findingIds":{"type":"array","items":{"type":"string"}},
			      "title":{"type":"string"},"rationale":{"type":"string"},"priority":{"enum":["LOW","MEDIUM","HIGH","CRITICAL"]},
			      "risk":{"enum":["LOW","MEDIUM","HIGH","CRITICAL"]},"benefit":{"enum":["LOW","MEDIUM","HIGH","CRITICAL"]},
			      "scope":{"type":"array","items":{"type":"string"}},"dependencies":{"type":"array","items":{"type":"string"}},
			      "suggestedExecutionMode":{"enum":["READ_ONLY","READ_WRITE"]},"approvalRequired":{"type":"boolean"},
			      "evidenceRefs":{"type":"array","items":{"$ref":"#/$defs/evidence"}},"confidence":{"type":"number","minimum":0,"maximum":1},
			      "recommendedNextAction":{"$ref":"#/$defs/action"}}}}
			  },
			  "$defs":{
			    "evidence":{"type":"object","additionalProperties":false,"required":["type","ref","label","artifactType","uri","line","contentHash"],"properties":{
			      "type":{"enum":["EXECUTION_RECORD","ARTIFACT","SOURCE_FILE","TIMELINE_EVENT","MEMORY","URL"]},"ref":{"type":"string"},
			      "label":{"type":["string","null"]},"artifactType":{"type":["string","null"]},"uri":{"type":["string","null"]},
			      "line":{"type":["integer","null"]},"contentHash":{"type":["string","null"]}}},
			    "action":{"type":"object","additionalProperties":false,
			      "required":["actionId","title","description","goal","acceptanceCriteria","scope","dependencies","suggestedExecutionMode","approvalRequired","estimatedComplexity"],
			      "properties":{"actionId":{"type":"string"},"title":{"type":"string"},"description":{"type":"string"},"goal":{"type":"string"},
			      "acceptanceCriteria":{"type":"array","items":{"type":"string"}},"scope":{"type":"array","items":{"type":"string"}},
			      "dependencies":{"type":"array","items":{"type":"string"}},"suggestedExecutionMode":{"enum":["READ_ONLY","READ_WRITE"]},
			      "approvalRequired":{"type":"boolean"},"estimatedComplexity":{"enum":["SMALL","MEDIUM","LARGE"]}}}
			  }
			}
			""");
	}

	public String path() {
		return path(false);
	}

	public String path(boolean projectAnalysis) {
		if (schemaPath == null) {
			throw new IllegalStateException("Codex output schema is not initialized");
		}
		return (projectAnalysis ? analysisSchemaPath : schemaPath).toString();
	}

	@PreDestroy
	void cleanup() throws IOException {
		if (schemaPath != null) {
			Files.deleteIfExists(schemaPath);
		}
		if (analysisSchemaPath != null) Files.deleteIfExists(analysisSchemaPath);
	}
}
