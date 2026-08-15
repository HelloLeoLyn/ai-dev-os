package com.aidevos.orchestrator.analysis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisProjectionCoordinator {
	private static final Logger logger = LoggerFactory.getLogger(AnalysisProjectionCoordinator.class);
	private final AnalysisInsightService service;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	public AnalysisProjectionCoordinator(AnalysisInsightService service) { this.service=service; }
	public void schedule(String taskId) { executor.submit(() -> {
		try { service.project(taskId); }
		catch (RuntimeException exception) { logger.warn("Analysis projection skipped task={}", taskId, exception); }
	}); }
	@EventListener(ApplicationReadyEvent.class)
	public void recoverInterrupted() { service.recoverInterrupted(); }
	@PreDestroy void close() { executor.close(); }
}
