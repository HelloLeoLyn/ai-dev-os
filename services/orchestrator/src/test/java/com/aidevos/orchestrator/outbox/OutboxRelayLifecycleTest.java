package com.aidevos.orchestrator.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OutboxRelay lifecycle validation: the background scheduler must stop on bean
 * destruction, must not run further ticks after stop, and must not resume after
 * stop. This protects later tests from a relay that keeps connecting to a
 * Testcontainers PostgreSQL instance that has already been stopped.
 */
class OutboxRelayLifecycleTest {

	private static final Instant START = Instant.parse("2026-08-04T08:00:00Z");
	private static final Duration BASE = Duration.ofSeconds(1);
	private static final Duration MAX = Duration.ofMinutes(1);

	@Test
	void stopTerminatesSchedulerAndPreventsFurtherTicks() throws Exception {
		CountingRepository repository = countingRepository();
		OutboxRelay relay = relay(repository);

		relay.start();
		awaitClaims(repository, 1);

		relay.stop();

		int claimsAfterStop = repository.claimCount();
		Thread.sleep(80);
		assertEquals(claimsAfterStop, repository.claimCount(),
			"no tick may run after stop() returned");
	}

	@Test
	void startAfterStopDoesNotScheduleAnyTick() throws Exception {
		CountingRepository repository = countingRepository();
		OutboxRelay relay = relay(repository);

		relay.stop();
		relay.start();

		Thread.sleep(80);
		assertEquals(0, repository.claimCount(),
			"start() after stop() must not resume the scheduler");
	}

	@Test
	void stopWaitsForInFlightTickBeforeReturning() throws Exception {
		BlockingRepository repository = blockingRepository();
		OutboxRelay relay = relay(repository);

		relay.start();
		assertTrue(repository.entered().await(2, TimeUnit.SECONDS),
			"relay tick must have started");

		Thread stopper = new Thread(relay::stop, "relay-stopper");
		stopper.start();

		// Deterministic signal: shutdownNow interrupts the blocked tick and the
		// repository records the interrupt while keeping the tick running. At
		// that point stop() is guaranteed to be inside awaitTermination.
		assertTrue(repository.interrupted().await(2, TimeUnit.SECONDS),
			"stop() must have signalled shutdown to the in-flight tick");
		assertTrue(stopper.isAlive(),
			"stop() must wait for the in-flight tick to finish");

		repository.release().countDown();
		stopper.join(TimeUnit.SECONDS.toMillis(5));
		assertFalse(stopper.isAlive(), "stop() must return once the tick finished");
	}

	private CountingRepository countingRepository() {
		return new CountingRepository(
			new InMemoryOutboxRepository(new TestClock(START)));
	}

	private BlockingRepository blockingRepository() {
		return new BlockingRepository(new InMemoryOutboxRepository(new TestClock(START)));
	}

	private OutboxRelay relay(OutboxRepository repository) {
		return new OutboxRelay(repository, new InMemoryOutboxTransactions(),
			List.of(new RecordingConsumer("audit")), new TestClock(START),
			Duration.ofMillis(1), 10, 8, BASE, MAX);
	}

	private static void awaitClaims(CountingRepository repository, int minimum) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (repository.claimCount() < minimum && System.nanoTime() < deadline) {
			Thread.sleep(5);
		}
		assertTrue(repository.claimCount() >= minimum,
			"scheduler should have claimed at least " + minimum + " times");
	}

	/** Delegating repository that counts claimPending invocations. */
	private static class CountingRepository implements OutboxRepository {

		private final OutboxRepository delegate;
		private final AtomicInteger claims = new AtomicInteger();

		CountingRepository(OutboxRepository delegate) {
			this.delegate = delegate;
		}

		int claimCount() {
			return claims.get();
		}

		@Override
		public OutboxMessage enqueue(String topic, String idempotencyKey, String payload) {
			return delegate.enqueue(topic, idempotencyKey, payload);
		}

		@Override
		public OutboxMessage find(String idempotencyKey) {
			return delegate.find(idempotencyKey);
		}

		@Override
		public List<OutboxMessage> claimPending(Instant now, int limit) {
			claims.incrementAndGet();
			return delegate.claimPending(now, limit);
		}

		@Override
		public boolean markPublished(String idempotencyKey) {
			return delegate.markPublished(idempotencyKey);
		}

		@Override
		public boolean markFailed(String idempotencyKey, String error, Instant nextAttemptAt) {
			return delegate.markFailed(idempotencyKey, error, nextAttemptAt);
		}

		@Override
		public boolean markDeadLettered(String idempotencyKey, String error) {
			return delegate.markDeadLettered(idempotencyKey, error);
		}

		@Override
		public long pendingCount() {
			return delegate.pendingCount();
		}

		@Override
		public long deadLetteredCount() {
			return delegate.deadLetteredCount();
		}
	}

	/**
	 * Repository whose claim blocks until the test releases it, ignoring
	 * interrupts so it models a JDBC call that does not abort promptly.
	 */
	private static final class BlockingRepository extends CountingRepository {

		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch interrupted = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);

		BlockingRepository(OutboxRepository delegate) {
			super(delegate);
		}

		CountDownLatch entered() {
			return entered;
		}

		CountDownLatch interrupted() {
			return interrupted;
		}

		CountDownLatch release() {
			return release;
		}

		@Override
		public List<OutboxMessage> claimPending(Instant now, int limit) {
			entered.countDown();
			boolean wasInterrupted = false;
			while (true) {
				try {
					release.await();
					break;
				}
				catch (InterruptedException ignored) {
					wasInterrupted = true;
					this.interrupted.countDown();
				}
			}
			if (wasInterrupted) {
				Thread.currentThread().interrupt();
			}
			return super.claimPending(now, limit);
		}
	}

	private record RecordingConsumer(String topic) implements OutboxConsumer {
		@Override
		public String topic() {
			return topic;
		}

		@Override
		public void consume(OutboxMessage message) {
			// no-op for lifecycle validation
		}
	}

	private static final class TestClock extends Clock {
		private final Instant instant;

		TestClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
