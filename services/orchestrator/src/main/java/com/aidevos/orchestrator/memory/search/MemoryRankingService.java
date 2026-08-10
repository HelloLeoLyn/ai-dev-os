package com.aidevos.orchestrator.memory.search;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryType;
import org.springframework.stereotype.Component;

/**
 * Rule-based memory ranking: relevance by keyword similarity, then priority
 * (successful experience and resolved bugs rank above failures), an agent /
 * task type context bonus, and a time decay so recent experience ranks above
 * older experience. Pure in-memory ranking; no vector index is involved.
 */
@Component
public class MemoryRankingService {

	private static final int SUMMARY_LIMIT = 160;

	public List<MemoryMatch> rank(List<MemoryRecord> records, MemoryQuery query) {
		if (records == null || records.isEmpty()) {
			return List.of();
		}
		String searchText = query == null || query.query() == null || query.query().isBlank()
			? "" : query.query();
		List<String> tokens = tokens(searchText);
		double now = System.currentTimeMillis();
		List<MemoryMatch> matches = new ArrayList<>();
		for (MemoryRecord record : records) {
			String content = text(record.getContent());
			String key = text(record.getKey());
			String solution = text(record.getSolution());
			double similarity = similarity(tokens, content + " " + key + " " + solution);
			double priority = priority(record);
			double context = contextBonus(record, query);
			double decay = decay(record.getCreatedAt() == null ? Instant.now()
				: record.getCreatedAt(), now);
			double score = (0.5 * similarity + 0.3 * priority + 0.2 * context) * decay;
			if (score <= 0) {
				continue;
			}
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("key", key);
			metadata.put("projectId", text(record.getProjectId()));
			if (record.getResolved() != null) {
				metadata.put("resolved", record.getResolved());
			}
			if (record.getCreatedAt() != null) {
				metadata.put("createdAt", record.getCreatedAt());
			}
			matches.add(new MemoryMatch(record.getId(), record.getType(), score,
				summary(content), solution.isEmpty() ? null : solution, metadata));
		}
		matches.sort(Comparator.comparingDouble(MemoryMatch::score).reversed());
		int limit = query == null ? 10 : query.limit();
		return matches.size() <= limit ? List.copyOf(matches)
			: List.copyOf(matches.subList(0, limit));
	}

	/**
	 * Fraction of query tokens present in the record text. Blank queries match
	 * everything with a neutral similarity so retrieval still returns the
	 * newest/highest priority records.
	 */
	private double similarity(List<String> tokens, String text) {
		if (tokens.isEmpty()) {
			return 0.5;
		}
		String haystack = text.toLowerCase(Locale.ROOT);
		int matched = 0;
		for (String token : tokens) {
			if (haystack.contains(token)) {
				matched++;
			}
		}
		return (double) matched / tokens.size();
	}

	/**
	 * Success experience (AGENT_EXPERIENCE) and resolved bugs rank highest,
	 * history tasks next, unresolved bugs lowest.
	 */
	private double priority(MemoryRecord record) {
		if (record.getType() == MemoryType.AGENT_EXPERIENCE) {
			return 1.0;
		}
		if (record.getType() == MemoryType.BUG_RECORD) {
			return Boolean.TRUE.equals(record.getResolved()) ? 1.0 : 0.4;
		}
		if (record.getType() == MemoryType.HISTORY_TASK) {
			return 0.8;
		}
		return 0.5;
	}

	/** +1.0 when the record mentions the queried agent or task type. */
	private double contextBonus(MemoryRecord record, MemoryQuery query) {
		if (query == null) {
			return 0.0;
		}
		String haystack = (text(record.getContent()) + " " + text(record.getKey()))
			.toLowerCase(Locale.ROOT);
		boolean agent = query.agentType() != null && !query.agentType().isBlank()
			&& haystack.contains(query.agentType().toLowerCase(Locale.ROOT));
		boolean task = query.taskType() != null && !query.taskType().isBlank()
			&& haystack.contains(query.taskType().toLowerCase(Locale.ROOT));
		return agent || task ? 1.0 : 0.0;
	}

	/** Halves the relevance every 30 days. */
	private double decay(Instant createdAt, double nowMillis) {
		long days = Math.max(0, ChronoUnit.DAYS.between(createdAt,
			Instant.ofEpochMilli((long) nowMillis)));
		return 1.0 / (1.0 + days / 30.0);
	}

	private List<String> tokens(String value) {
		String normalized = value.toLowerCase(Locale.ROOT)
			.replaceAll("[^\\p{L}\\p{N}]+", " ");
		String[] parts = normalized.trim().split("\\s+");
		List<String> result = new ArrayList<>();
		for (String part : parts) {
			if (!part.isBlank() && part.length() > 1) {
				result.add(part);
			}
		}
		return result;
	}

	private String summary(String content) {
		String singleLine = content.replace('\n', ' ').replace('\r', ' ').trim();
		if (singleLine.length() <= SUMMARY_LIMIT) {
			return singleLine;
		}
		return singleLine.substring(0, SUMMARY_LIMIT);
	}

	private String text(String value) {
		return value == null ? "" : value;
	}
}
