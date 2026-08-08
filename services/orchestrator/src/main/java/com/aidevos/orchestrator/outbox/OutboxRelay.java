package com.aidevos.orchestrator.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Background outbox relay. Each tick claims a bounded batch of pending rows
 * and publishes them through the topic consumer inside one transaction per
 * message. A failing consumer schedules the row with exponential backoff and
 * dead-letters it after {@code maxAttempts}, without ever rolling back the
 * business transaction that committed the row.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class OutboxRelay {

	private static final Logger logger = LoggerFactory.getLogger(OutboxRelay.class);

	private final OutboxRepository outboxRepository;
	private final OutboxTransactions transactions;
	private final Map<String, OutboxConsumer> consumers;
	private final Clock clock;
	private final Duration interval;
	private final int batchSize;
	private final int maxAttempts;
	private final Duration backoffBase;
	private final Duration backoffMax;
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

	private volatile boolean stopRequested;
	private volatile ScheduledExecutorService scheduler;

	@Autowired
	public OutboxRelay(OutboxRepository outboxRepository, OutboxTransactions transactions,
			List<OutboxConsumer> consumers,
			@Value("${outbox.relay.interval:1s}") Duration interval,
			@Value("${outbox.relay.batch-size:100}") int batchSize,
			@Value("${outbox.relay.max-attempts:8}") int maxAttempts,
			@Value("${outbox.relay.backoff-base:1s}") Duration backoffBase,
			@Value("${outbox.relay.backoff-max:60s}") Duration backoffMax) {
		this(outboxRepository, transactions, consumers, Clock.systemUTC(), interval, batchSize,
			maxAttempts, backoffBase, backoffMax);
	}

	OutboxRelay(OutboxRepository outboxRepository, OutboxTransactions transactions,
			List<OutboxConsumer> consumers, Clock clock, Duration interval, int batchSize,
			int maxAttempts, Duration backoffBase, Duration backoffMax) {
		this.outboxRepository = outboxRepository;
		this.transactions = transactions;
		this.consumers = new HashMap<>();
		for (OutboxConsumer consumer : consumers) {
			this.consumers.put(consumer.topic(), consumer);
		}
		this.clock = clock;
		this.interval = interval;
		this.batchSize = batchSize;
		this.maxAttempts = maxAttempts;
		this.backoffBase = backoffBase;
		this.backoffMax = backoffMax;
	}

	@PostConstruct
	void start() {
		if (stopRequested || scheduler != null) {
			return;
		}
		scheduler = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("outbox-relay").factory());
		scheduler.scheduleWithFixedDelay(this::tick, 0,
			Math.max(1, interval.toMillis()), TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	void stop() {
		stopRequested = true;
		ScheduledExecutorService active = scheduler;
		if (active == null) {
			return;
		}
		active.shutdownNow();
		try {
			if (!active.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				logger.warn("Outbox relay scheduler did not terminate within {}", SHUTDOWN_TIMEOUT);
			}
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupted while stopping outbox relay");
		}
	}

	void tick() {
		if (stopRequested || Thread.currentThread().isInterrupted()) {
			return;
		}
		try {
			List<OutboxMessage> pending = outboxRepository.claimPending(clock.instant(), batchSize);
			for (OutboxMessage message : pending) {
				OutboxConsumer consumer = consumers.get(message.topic());
				if (consumer == null) {
					fail(message, new IllegalStateException(
						"No consumer registered for outbox topic " + message.topic()));
					continue;
				}
				try {
					transactions.execute(() -> {
						consumer.consume(message);
						outboxRepository.markPublished(message.idempotencyKey());
						return null;
					});
				}
				catch (RuntimeException exception) {
					fail(message, exception);
				}
			}
		}
		catch (RuntimeException exception) {
			if (!stopRequested && !Thread.currentThread().isInterrupted()) {
				logger.warn("Outbox relay tick failed", exception);
			}
		}
	}

	private void fail(OutboxMessage message, RuntimeException failure) {
		int attempts = message.attempts() + 1;
		if (attempts >= maxAttempts) {
			try {
				outboxRepository.markDeadLettered(message.idempotencyKey(), failure.getMessage());
			}
			catch (RuntimeException markFailure) {
				logger.warn("Outbox dead-letter failed for key={}",
					message.idempotencyKey(), markFailure);
			}
			logger.error("Outbox message dead-lettered topic={} key={} attempts={} error={}",
				message.topic(), message.idempotencyKey(), attempts, failure.getMessage());
			return;
		}
		Instant nextAttemptAt = clock.instant().plus(backoff(attempts));
		try {
			outboxRepository.markFailed(message.idempotencyKey(), failure.getMessage(),
				nextAttemptAt);
		}
		catch (RuntimeException markFailure) {
			logger.warn("Outbox mark-failed failed for key={}",
				message.idempotencyKey(), markFailure);
		}
		logger.warn("Outbox message publish failed topic={} key={} attempts={} next={} error={}",
			message.topic(), message.idempotencyKey(), attempts, nextAttemptAt, failure.getMessage());
	}

	private Duration backoff(int attempts) {
		long multiplier = 1L << Math.min(attempts - 1, 20);
		Duration delay = backoffBase.multipliedBy(multiplier);
		return delay.compareTo(backoffMax) > 0 ? backoffMax : delay;
	}
}
