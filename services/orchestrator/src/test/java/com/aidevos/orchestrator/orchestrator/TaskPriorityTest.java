package com.aidevos.orchestrator.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task priority verification: parsing and the ordering contract used by the
 * task queue (CRITICAL first, LOW last).
 */
class TaskPriorityTest {

	@Test
	void parsesKnownPrioritiesCaseInsensitively() {
		assertEquals(TaskPriority.CRITICAL, TaskPriority.from("CRITICAL"));
		assertEquals(TaskPriority.HIGH, TaskPriority.from("high"));
		assertEquals(TaskPriority.NORMAL, TaskPriority.from("Normal"));
		assertEquals(TaskPriority.LOW, TaskPriority.from("low"));
	}

	@Test
	void unknownOrBlankPriorityFallsBackToNormal() {
		assertEquals(TaskPriority.NORMAL, TaskPriority.from("urgent"));
		assertEquals(TaskPriority.NORMAL, TaskPriority.from(null));
		assertEquals(TaskPriority.NORMAL, TaskPriority.from(" "));
	}

	@Test
	void higherThanFollowsOrdinalOrder() {
		assertTrue(TaskPriority.CRITICAL.higherThan(TaskPriority.HIGH));
		assertTrue(TaskPriority.HIGH.higherThan(TaskPriority.NORMAL));
		assertTrue(TaskPriority.NORMAL.higherThan(TaskPriority.LOW));
		assertFalse(TaskPriority.LOW.higherThan(TaskPriority.NORMAL));
		assertFalse(TaskPriority.NORMAL.higherThan(TaskPriority.NORMAL));
	}
}
