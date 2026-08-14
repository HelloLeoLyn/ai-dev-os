package com.aidevos.orchestrator.backlog;

import com.aidevos.orchestrator.taskcenter.TaskRecord;

public record BacklogConversionResult(BacklogItem backlogItem, TaskRecord task) { }
