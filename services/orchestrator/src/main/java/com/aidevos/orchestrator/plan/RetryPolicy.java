package com.aidevos.orchestrator.plan;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record RetryPolicy(int maxAttempts, Duration initialDelay,
		List<String> retryableErrorCodes) {

	public RetryPolicy {
		initialDelay = initialDelay == null ? Duration.ZERO : initialDelay;
		retryableErrorCodes = retryableErrorCodes == null ? List.of()
			: List.copyOf(new ArrayList<>(retryableErrorCodes));
	}

	public static RetryPolicy noRetry() {
		return new RetryPolicy(1, Duration.ZERO, List.of());
	}
}
