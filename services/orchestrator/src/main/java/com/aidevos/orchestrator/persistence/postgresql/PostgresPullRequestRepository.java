package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;

import com.aidevos.orchestrator.pr.PullRequestRecord;
import com.aidevos.orchestrator.pr.PullRequestRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * V1-PR-PERSISTENCE-CLOSEOUT：PullRequestRecord 的 PostgreSQL 持久化实现。
 *
 * 复用 PostgresDocumentStore（repository_type = "pull-request"，无新 migration）。
 * 与 InMemoryPullRequestRepository（matchIfMissing）条件互斥：
 *   - aidevos.persistence.type=postgresql → 本实现
 *   - 其他 / 缺省 → InMemoryPullRequestRepository
 *
 * 重启语义：pipeline 持久化的 pullRequestId 重启后仍可解析（get），
 * CI_CHECKING 的 PullRequestService.get(prId) 不再命中空内存仓库。
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
final class PostgresPullRequestRepository implements PullRequestRepository {

	private static final String TYPE = "pull-request";

	private volatile PostgresDocumentStore store;
	private final javax.sql.DataSource source;
	private final tools.jackson.databind.ObjectMapper mapper;

	PostgresPullRequestRepository(javax.sql.DataSource source,
			tools.jackson.databind.ObjectMapper mapper) {
		this.source = source;
		this.mapper = mapper;
	}

	private PostgresDocumentStore store() {
		if (store == null) {
			synchronized (this) {
				if (store == null) {
					store = new PostgresDocumentStore(source, mapper);
				}
			}
		}
		return store;
	}

	@Override
	public void save(PullRequestRecord record) {
		store().put(TYPE, record.getPullRequestId(),
			PersistenceSnapshots.PullRequest.of(record), "task:" + record.getTaskId());
	}

	@Override
	public PullRequestRecord get(String pullRequestId) {
		var snapshot = store().get(TYPE, pullRequestId, PersistenceSnapshots.PullRequest.class);
		return snapshot == null ? null : snapshot.value();
	}

	@Override
	public PullRequestRecord getByCommitId(String commitId) {
		if (commitId == null) {
			return null;
		}
		return store().all(TYPE, PersistenceSnapshots.PullRequest.class).stream()
			.map(PersistenceSnapshots.PullRequest::value)
			.filter(record -> commitId.equals(record.getCommitId()))
			.findFirst().orElse(null);
	}

	@Override
	public List<PullRequestRecord> getByTaskId(String taskId) {
		return store().allBySecondary(TYPE, "task:" + taskId,
			PersistenceSnapshots.PullRequest.class).stream()
			.map(PersistenceSnapshots.PullRequest::value)
			.toList();
	}

	@Override
	public List<PullRequestRecord> list() {
		return store().all(TYPE, PersistenceSnapshots.PullRequest.class).stream()
			.map(PersistenceSnapshots.PullRequest::value)
			.toList();
	}
}
