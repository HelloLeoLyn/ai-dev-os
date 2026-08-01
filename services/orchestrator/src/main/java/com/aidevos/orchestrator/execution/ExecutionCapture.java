package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.model.ExecutionRecord;

public record ExecutionCapture<T>(T result, ExecutionRecord executionRecord) {
}
