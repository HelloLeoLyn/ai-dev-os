package com.aidevos.orchestrator.repair;

/**
 * Bounds for the automatic repair loop. MAX_RETRY caps the number of
 * fix-and-verify attempts; a repair that exceeds it becomes FAILED so the
 * loop can never run forever.
 */
public final class RepairPolicy {

	public static final int MAX_RETRY = 3;

	private RepairPolicy() {
	}
}
