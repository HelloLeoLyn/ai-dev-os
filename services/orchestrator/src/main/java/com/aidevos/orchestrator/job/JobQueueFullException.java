package com.aidevos.orchestrator.job;

public class JobQueueFullException extends RuntimeException {

	public JobQueueFullException() {
		super("Job queue is full");
	}
}
