package com.aidevos.orchestrator.backlog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryBacklogRepository implements BacklogRepository {
	private final Map<String, BacklogItem> items = new ConcurrentHashMap<>();
	@Override public void save(BacklogItem item) { items.put(item.getBacklogItemId(), item); }
	@Override public BacklogItem get(String id) { return items.get(id); }
	@Override public List<BacklogItem> list() { return sorted(items.values().stream().toList()); }
	@Override public List<BacklogItem> listByProjectId(String projectId) {
		return sorted(items.values().stream().filter(item -> projectId.equals(item.getProjectId())).toList());
	}
	private List<BacklogItem> sorted(java.util.Collection<BacklogItem> values) {
		List<BacklogItem> result = new ArrayList<>(values);
		result.sort(Comparator.comparing(BacklogItem::getUpdatedAt).reversed());
		return result;
	}
}
