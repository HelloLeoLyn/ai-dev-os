package com.aidevos.orchestrator.operation;

import java.time.Duration;
import java.util.Map;

/** A structured, allowlisted operation. It intentionally has no cwd/command field. */
public record OperationSpec(String operation, Map<String, Object> args, Duration timeout) {
    public OperationSpec {
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation is required");
        args = args == null ? Map.of() : Map.copyOf(args);
        timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    }
}
