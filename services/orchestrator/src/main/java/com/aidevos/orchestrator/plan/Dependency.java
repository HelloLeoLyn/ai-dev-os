package com.aidevos.orchestrator.plan;

public record Dependency(String fromStepId, String toStepId, boolean required) {
}
