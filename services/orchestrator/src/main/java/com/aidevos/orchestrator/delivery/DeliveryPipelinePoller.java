package com.aidevos.orchestrator.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V1-DELIVERY-AUTO-ADVANCE-CLOSEOUT：服务端 Delivery 自动推进器。
 *
 * 职责唯一：定期找到需要后台继续推进的 DeliveryPipeline，调用现有
 * DeliveryPipelineService.advance(taskId)。不复制任何状态机逻辑；
 * Validation / Commit / Push / PR / CI 全部仍由 DeliveryPipelineService 负责。
 *
 * 扫描范围（V1 最小收窄）：currentStage = CI_CHECKING 且 status = RUNNING。
 * WAITING_APPROVAL / FAILED / COMPLETE 及其余 stage 一律跳过——人工 gate 不自动
 * 推进，失败不自动恢复，完成后不再 poll。
 *
 * advance() 本身幂等（reconcile 基于已产生实体），单实例单 tick 每个 task 最多
 * 调用一次（repository.list() 无重复）。调度间隔可用
 * aidevos.delivery.poll-interval-ms 覆盖，默认 15s。
 */
@Component
public class DeliveryPipelinePoller {

	private static final Logger log = LoggerFactory.getLogger(DeliveryPipelinePoller.class);

	private final DeliveryPipelineRepository repository;
	private final DeliveryPipelineService pipelineService;

	public DeliveryPipelinePoller(DeliveryPipelineRepository repository,
			DeliveryPipelineService pipelineService) {
		this.repository = repository;
		this.pipelineService = pipelineService;
	}

	@Scheduled(fixedDelayString = "${aidevos.delivery.poll-interval-ms:15000}")
	public void poll() {
		for (DeliveryPipeline pipeline : repository.list()) {
			if (pipeline.getStatus() != DeliveryStatus.RUNNING
					|| pipeline.getCurrentStage() != DeliveryStage.CI_CHECKING) {
				continue;
			}
			String taskId = pipeline.getTaskId();
			try {
				pipelineService.advance(taskId);
			}
			catch (RuntimeException exception) {
				log.warn("Delivery advance failed for task {}: {}", taskId,
					exception.getMessage());
			}
		}
	}
}
