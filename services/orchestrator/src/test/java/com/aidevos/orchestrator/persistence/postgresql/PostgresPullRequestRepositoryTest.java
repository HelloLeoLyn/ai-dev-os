package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Instant;
import java.util.Optional;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.change.ChangeService;
import com.aidevos.orchestrator.ci.CiRunRecord;
import com.aidevos.orchestrator.ci.CiService;
import com.aidevos.orchestrator.ci.CiStatus;
import com.aidevos.orchestrator.commit.CommitRecord;
import com.aidevos.orchestrator.commit.CommitService;
import com.aidevos.orchestrator.delivery.DeliveryPipeline;
import com.aidevos.orchestrator.delivery.DeliveryPipelineService;
import com.aidevos.orchestrator.delivery.DeliveryStage;
import com.aidevos.orchestrator.delivery.DeliveryStatus;
import com.aidevos.orchestrator.delivery.InMemoryDeliveryPipelineRepository;
import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestService;
import com.aidevos.orchestrator.pr.PullRequestStatus;
import com.aidevos.orchestrator.pr.provider.GitProvider;
import com.aidevos.orchestrator.pr.provider.GitProviderProperties;
import com.aidevos.orchestrator.qualitygate.QualityGateService;
import com.aidevos.orchestrator.remote.RemoteGitService;
import com.aidevos.orchestrator.remote.RemotePushApprovalService;
import com.aidevos.orchestrator.validation.ValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V1-PR-PERSISTENCE-CLOSEOUT：PullRequest PostgreSQL 持久化验证。
 * FakeDocumentDataSource 在纯单元测试中真实运行 PostgresDocumentStore，
 * 跨实例共享同一 DataSource 即等价于"重启后 repository/service 重建"。
 */
class PostgresPullRequestRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private FakeDocumentDataSource dataSource;
	private ObjectMapper mapper;

	@BeforeEach
	void setUp() {
		dataSource = new FakeDocumentDataSource();
		mapper = new ObjectMapper();
	}

	private PostgresPullRequestRepository repository() {
		return new PostgresPullRequestRepository(dataSource, mapper);
	}

	private PullRequestRecord pr(String id, String commitId, String status) {
		PullRequestRecord record = new PullRequestRecord(id, "task-d", commitId, "remote-d",
			"ai-dev-os/task/task-d", "main", "Title " + id, "Desc", "https://pr/" + id, NOW);
		if ("OPEN".equals(status)) {
			record.markOpened();
		}
		record.updateExternalId("ext-" + id);
		return record;
	}

	/** A. save PullRequest → get → 字段完整 */
	@Test
	void saveThenGetPreservesAllFields() {
		PostgresPullRequestRepository repository = repository();
		PullRequestRecord original = pr("pr-1", "commit-1", "OPEN");
		original.updateUrl("https://pr/updated");

		repository.save(original);
		PullRequestRecord restored = repository.get("pr-1");

		assertNotNull(restored);
		assertEquals("pr-1", restored.getPullRequestId());
		assertEquals("task-d", restored.getTaskId());
		assertEquals("commit-1", restored.getCommitId());
		assertEquals("remote-d", restored.getRemoteId());
		assertEquals("ai-dev-os/task/task-d", restored.getBranch());
		assertEquals("main", restored.getTargetBranch());
		assertEquals("Title pr-1", restored.getTitle());
		assertEquals("Desc", restored.getDescription());
		assertEquals("https://pr/updated", restored.getUrl());
		assertEquals(PullRequestStatus.OPEN, restored.getStatus());
		assertEquals("ext-pr-1", restored.getExternalId());
		assertEquals(NOW, restored.getCreatedAt());
		assertNotNull(restored.getUpdatedAt());
	}

	/** B. save PullRequest → getByCommitId → 返回同一 PR */
	@Test
	void getByCommitIdReturnsSavedPullRequest() {
		PostgresPullRequestRepository repository = repository();
		repository.save(pr("pr-1", "commit-1", "OPEN"));

		PullRequestRecord found = repository.getByCommitId("commit-1");

		assertNotNull(found);
		assertEquals("pr-1", found.getPullRequestId());
		assertEquals("commit-1", found.getCommitId());
	}

	/** C. 模拟 restart：repository/service 实例重建但共享 store → pullRequestId 仍可解析 */
	@Test
	void restartWithSharedStoreResolvesPersistedPullRequest() {
		repository().save(pr("pr-1", "commit-1", "OPEN"));

		PostgresPullRequestRepository restarted = repository(); // 新实例，同一 DataSource
		PullRequestRecord found = restarted.get("pr-1");
		PullRequestRecord byCommit = restarted.getByCommitId("commit-1");

		assertNotNull(found, "重启后 get(prId) 必须可解析");
		assertEquals("pr-1", found.getPullRequestId());
		assertNotNull(byCommit, "重启后 getByCommit 必须可复用原 PR");
		assertEquals("pr-1", byCommit.getPullRequestId());
	}

	/** D. CI_CHECKING + persisted PR + persisted CI RUNNING → advance → COMPLETE，不创建第二个 PR */
	@Test
	void restartThenAdvanceCompletesWithoutSecondPullRequest() {
		// 重启前创建的 PR 已持久化
		repository().save(pr("pr-d", "commit-d", "OPEN"));

		// 重启：重建 service 实例（共享 DataSource = 同一数据库）
		PullRequestService pullRequestService = new PullRequestService(repository(),
			mock(CommitService.class), mock(RemoteGitService.class), mock(GitProvider.class),
			new GitProviderProperties(), AuditService.noop());

		CommitService commitService = mock(CommitService.class);
		CommitRecord commit = new CommitRecord("commit-d", "change-d", "task-d", "ws-d",
			"ai-dev-os/task/task-d", "msg", NOW);
		commit.markCommitting();
		commit.markSuccess("hash-d");
		when(commitService.getCommit("commit-d")).thenReturn(Optional.of(commit));

		CiRunRecord ciRun = new CiRunRecord("ci-d", "task-d", "pr-d", "mock",
			"ai-dev-os/task/task-d", "hash-d", NOW);
		ciRun.markRunning();
		CiService ciService = mock(CiService.class);
		when(ciService.get("ci-d")).thenReturn(Optional.of(ciRun));
		when(ciService.check("pr-d", "hash-d")).thenAnswer(inv -> {
			if (ciRun.getStatus() == CiStatus.RUNNING) {
				ciRun.markSuccess();
			}
			return ciRun;
		});

		InMemoryDeliveryPipelineRepository pipelineRepository = new InMemoryDeliveryPipelineRepository();
		DeliveryPipelineService pipelineService = new DeliveryPipelineService(pipelineRepository,
			mock(ChangeService.class), mock(ValidationService.class),
			mock(QualityGateService.class), commitService, mock(RemoteGitService.class),
			mock(RemotePushApprovalService.class), pullRequestService, ciService,
			AuditService.noop());

		// 重启后恢复 pipeline（重启前持久化的全部绑定），停在 CI_CHECKING + RUNNING
		DeliveryPipeline pipeline = new DeliveryPipeline("task-d", NOW);
		pipeline.bindChangeSet("change-d");
		pipeline.bindExecutionWorkspace("ws-d");
		pipeline.bindValidation("validation-d");
		pipeline.bindQualityGate("gate-d");
		pipeline.bindCommit("commit-d");
		pipeline.bindApproval("approval-d");
		pipeline.bindPush("remote-d");
		pipeline.bindPullRequest("pr-d");
		pipeline.bindCiRun("ci-d");
		pipeline.advanceTo(DeliveryStage.CI_CHECKING);
		pipelineRepository.save(pipeline);

		pipelineService.advance("task-d");

		DeliveryPipeline done = pipelineRepository.get("task-d");
		assertEquals(DeliveryStatus.COMPLETE, done.getStatus(), "重启后 advance 必须到 COMPLETE");
		assertEquals(DeliveryStage.DELIVERY_COMPLETE, done.getCurrentStage());
		// 不创建第二个 PR：仓库仍只有 1 条，getByCommitId 仍是原 PR
		assertEquals(1, repository().list().size(), "不得创建第二个 PR");
		assertEquals("pr-d", repository().getByCommitId("commit-d").getPullRequestId());
	}
}
