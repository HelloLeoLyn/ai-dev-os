package com.aidevos.orchestrator.execution;

/**
 * Regression depth for a work package. FAST is the default for bugfixes and
 * small work packages. Only architecture changes, migrations, releases or an
 * explicit user request may escalate to FULL; agents must not escalate FAST
 * to FULL on their own.
 */
public enum ValidationProfile {
	FAST,
	TARGETED,
	FULL
}
