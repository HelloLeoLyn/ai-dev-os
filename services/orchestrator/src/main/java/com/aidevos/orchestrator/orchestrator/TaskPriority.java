package com.aidevos.orchestrator.orchestrator;

/**
 * Priority of an orchestrated task. CRITICAL sorts first, LOW last; the
 * task queue serves the highest priority first and keeps FIFO order within
 * the same priority.
 */
public enum TaskPriority {
	LOW,
	NORMAL,
	HIGH,
	CRITICAL;

	public static TaskPriority from(String value) {
		if (value == null || value.isBlank()) {
			return NORMAL;
		}
		try {
			return valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException exception) {
			return NORMAL;
		}
	}

	/** True when this priority ranks higher than the other. */
	public boolean higherThan(TaskPriority other) {
		return other != null && ordinal() > other.ordinal();
	}
}
