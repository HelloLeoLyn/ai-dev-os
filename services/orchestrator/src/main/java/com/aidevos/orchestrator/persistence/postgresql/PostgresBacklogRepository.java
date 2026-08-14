package com.aidevos.orchestrator.persistence.postgresql;

import java.util.List;
import javax.sql.DataSource;
import com.aidevos.orchestrator.backlog.BacklogItem;
import com.aidevos.orchestrator.backlog.BacklogRepository;
import tools.jackson.databind.ObjectMapper;

final class PostgresBacklogRepository implements BacklogRepository {
	private static final String TYPE = "backlog-item";
	private volatile PostgresDocumentStore store;
	private final DataSource dataSource;
	private final ObjectMapper mapper;

	PostgresBacklogRepository(DataSource dataSource, ObjectMapper mapper) {
		this.dataSource = dataSource;
		this.mapper = mapper;
	}

	PostgresBacklogRepository(PostgresDocumentStore store) {
		this.store = store;
		this.dataSource = null;
		this.mapper = null;
	}

	private PostgresDocumentStore store() {
		if (store == null) synchronized (this) {
			if (store == null) store = new PostgresDocumentStore(dataSource, mapper);
		}
		return store;
	}

	@Override public void save(BacklogItem item) {
		store().put(TYPE, item.getBacklogItemId(), item, item.getProjectId());
	}
	@Override public BacklogItem get(String id) { return store().get(TYPE, id, BacklogItem.class); }
	@Override public List<BacklogItem> list() { return store().all(TYPE, BacklogItem.class); }
	@Override public List<BacklogItem> listByProjectId(String projectId) {
		return store().allBySecondary(TYPE, projectId, BacklogItem.class);
	}
}
