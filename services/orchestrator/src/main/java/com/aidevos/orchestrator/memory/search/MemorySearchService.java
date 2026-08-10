package com.aidevos.orchestrator.memory.search;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.memory.MemoryContext;
import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryRepository;
import com.aidevos.orchestrator.memory.MemoryType;
import org.springframework.stereotype.Service;

/**
 * In-memory retrieval over project memory: BUG_RECORD, HISTORY_TASK and
 * AGENT_EXPERIENCE records are matched by keyword and ranked by the
 * MemoryRankingService. No vector database or external search index is used.
 */
@Service
public class MemorySearchService {

	private static final List<MemoryType> SEARCHABLE_TYPES = List.of(
		MemoryType.BUG_RECORD, MemoryType.HISTORY_TASK, MemoryType.AGENT_EXPERIENCE);

	private final MemoryRepository repository;
	private final MemoryRankingService rankingService;

	public MemorySearchService(MemoryRepository repository,
			MemoryRankingService rankingService) {
		this.repository = repository;
		this.rankingService = rankingService;
	}

	public List<MemoryMatch> search(MemoryQuery query) {
		if (query == null) {
			return List.of();
		}
		List<MemoryRecord> candidates = new ArrayList<>();
		if (query.taskType() != null && !query.taskType().isBlank()) {
			candidates.addAll(repository.list(query.projectId(), MemoryType.HISTORY_TASK));
		}
		for (MemoryType type : SEARCHABLE_TYPES) {
			candidates.addAll(repository.list(query.projectId(), type));
		}
		return rankingService.rank(deduplicate(candidates), query);
	}

	/**
	 * Builds the memory context for a task: similar historical tasks, known
	 * solutions, unresolved-issue warnings and recommended solutions.
	 */
	public MemoryContext taskContext(String taskId, String projectId, String query) {
		List<MemoryMatch> matches = search(new MemoryQuery(query, null, null, projectId, 10));
		MemoryContext context = new MemoryContext();
		context.setSimilarTasks(matches.stream()
			.filter(match -> match.type() == MemoryType.HISTORY_TASK).toList());
		context.setSolutions(matches.stream()
			.filter(match -> match.type() == MemoryType.BUG_RECORD
				|| match.type() == MemoryType.AGENT_EXPERIENCE).toList());
		context.setWarnings(matches.stream()
			.filter(match -> match.type() == MemoryType.BUG_RECORD
				&& !Boolean.TRUE.equals(match.metadata().get("resolved")))
			.map(MemoryMatch::summary)
			.limit(3).toList());
		context.setRecommendations(matches.stream()
			.map(MemoryMatch::solution)
			.filter(solution -> solution != null && !solution.isBlank())
			.limit(5).toList());
		return context;
	}

	/** Retrieves only one record type, ranked the same way. */
	public List<MemoryMatch> search(MemoryQuery query, MemoryType onlyType) {
		if (query == null || onlyType == null) {
			return List.of();
		}
		return rankingService.rank(repository.list(query.projectId(), onlyType), query);
	}

	private List<MemoryRecord> deduplicate(List<MemoryRecord> records) {
		List<MemoryRecord> result = new ArrayList<>();
		for (MemoryRecord record : records) {
			if (record != null && record.getId() != null
				&& result.stream().noneMatch(existing -> existing.getId().equals(record.getId()))) {
				result.add(record);
			}
		}
		return result;
	}
}
