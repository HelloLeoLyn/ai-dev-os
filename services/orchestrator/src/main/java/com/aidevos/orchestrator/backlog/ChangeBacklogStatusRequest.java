package com.aidevos.orchestrator.backlog;

public record ChangeBacklogStatusRequest(BacklogStatus status, String blockedReason) { }
