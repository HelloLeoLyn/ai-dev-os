package com.aidevos.orchestrator.qualitygate;
public record QualityGateReason(String code, String severity, String message,
	String sourceType, String sourceId, boolean blocking) { }
