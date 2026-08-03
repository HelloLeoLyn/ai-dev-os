package com.aidevos.orchestrator.audit;

class InMemoryAuditRepositoryTest extends AuditRepositoryContract {
	private final AuditRepository repository = new InMemoryAuditRepository();
	@Override AuditRepository repository() { return repository; }
}
