package com.aidevos.orchestrator.persistence;

import com.aidevos.orchestrator.job.JobStore;

class InMemoryLeaseableJobRepositoryContractTest extends LeaseableJobRepositoryContractTest {

	private final JobStore store = new JobStore();

	@Override
	protected LeaseableJobRepository repository() {
		return store;
	}
}
