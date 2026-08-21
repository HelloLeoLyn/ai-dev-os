package com.aidevos.orchestrator.taskcenter;

/**
 * V1-FINAL-CLOSEOUT：MODE_CONFLICT preflight 错误。
 *
 * READ_ONLY 模式下收到明显需要写代码的任务（CODE_GENERATION）时，
 * 在进入实际 AI 执行前 fail closed，明确报 MODE_CONFLICT，
 * 避免后续表现成 model/executor 错误。
 */
public class TaskModeConflictException extends RuntimeException {

	public TaskModeConflictException(String message) {
		super(message);
	}
}
