package com.aidevos.orchestrator.analysis;

public final class AnalysisEnums {
	private AnalysisEnums() { }
	public enum Level { LOW, MEDIUM, HIGH, CRITICAL }
	public enum ExtractorType { STRUCTURED, AI, LEGACY_TEXT }
	public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }
	public enum EvidenceType { EXECUTION_RECORD, ARTIFACT, SOURCE_FILE, TIMELINE_EVENT, MEMORY, URL }
	public enum EstimatedComplexity { SMALL, MEDIUM, LARGE }
}
