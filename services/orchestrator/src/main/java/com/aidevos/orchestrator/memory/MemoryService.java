package com.aidevos.orchestrator.memory;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Long-term project memory: save, query, update and delete memory records.
 * Read/write through the configured MemoryRepository (in-memory or
 * PostgreSQL). Does not touch the execution chain; agents can read project
 * rules, history tasks and bug records through the query API.
 */
@Service
public class MemoryService {

	private final MemoryRepository repository;

	public MemoryService(MemoryRepository repository) {
		this.repository = repository;
	}

	public MemoryRecord create(MemoryRecord record) {
		validate(record);
		if (isBlank(record.getId())) {
			record.setId(UUID.randomUUID().toString());
		}
		if (isBlank(record.getProjectId())) {
			record.setProjectId("default");
		}
		Instant now = Instant.now();
		record.setCreatedAt(now);
		record.setUpdatedAt(now);
		repository.save(record);
		return record;
	}

	public MemoryRecord update(String id, MemoryRecord changes) {
		MemoryRecord existing = repository.get(id);
		if (existing == null) {
			throw new IllegalArgumentException("Memory record not found: " + id);
		}
		if (!isBlank(changes.getProjectId())) {
			existing.setProjectId(changes.getProjectId());
		}
		if (changes.getType() != null) {
			existing.setType(changes.getType());
		}
		if (!isBlank(changes.getKey())) {
			existing.setKey(changes.getKey());
		}
		if (changes.getContent() != null) {
			existing.setContent(changes.getContent());
		}
		if (changes.getResolved() != null) {
			existing.setResolved(changes.getResolved());
		}
		if (changes.getSolution() != null) {
			existing.setSolution(changes.getSolution());
		}
		existing.setUpdatedAt(Instant.now());
		validate(existing);
		repository.save(existing);
		return existing;
	}

	public MemoryRecord get(String id) {
		if (isBlank(id)) {
			return null;
		}
		return repository.get(id);
	}

	public List<MemoryRecord> list(String projectId, MemoryType type) {
		return repository.list(projectId, type);
	}

	public boolean delete(String id) {
		if (isBlank(id)) {
			return false;
		}
		return repository.delete(id);
	}

	private void validate(MemoryRecord record) {
		if (record.getType() == null) {
			throw new IllegalArgumentException("Memory type is required");
		}
		if (isBlank(record.getKey())) {
			throw new IllegalArgumentException("Memory key is required");
		}
		if (isBlank(record.getContent())) {
			throw new IllegalArgumentException("Memory content is required");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
