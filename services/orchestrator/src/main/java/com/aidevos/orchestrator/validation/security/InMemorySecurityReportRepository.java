package com.aidevos.orchestrator.validation.security;
import java.util.List; import java.util.Map; import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository;
@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="in-memory",matchIfMissing=true)
public class InMemorySecurityReportRepository implements SecurityReportRepository {
	private final Map<String,SecurityReport> values=new ConcurrentHashMap<>(); public void save(SecurityReport r){values.put(r.getReportId(),r);} public SecurityReport get(String id){return values.get(id);}
	public List<SecurityReport> findByValidationRunId(String id){return values.values().stream().filter(r->id.equals(r.getValidationRunId())).toList();}
}
