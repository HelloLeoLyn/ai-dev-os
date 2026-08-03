package com.aidevos.orchestrator.persistence;

import java.util.List;

public interface CrudRepository<T> {

	void save(T entity);

	T get(String id);

	List<T> getAll();

	default void remove(String id) {
		throw new UnsupportedOperationException("Remove is not supported");
	}
}
