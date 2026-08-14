package com.aidevos.orchestrator.backlog;

import java.util.List;

public interface BacklogRepository {
	void save(BacklogItem item);
	BacklogItem get(String id);
	List<BacklogItem> list();
	List<BacklogItem> listByProjectId(String projectId);
}
