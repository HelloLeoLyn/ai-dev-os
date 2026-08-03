package com.aidevos.orchestrator.execution;
import java.util.List; import com.aidevos.orchestrator.model.ExecutionRecord; import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository;
@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
public class PostgresExecutionRecordRepository implements ExecutionRecordRepository {
	private static final String TYPE="execution-record"; private final PostgresDocumentStore store; public PostgresExecutionRecordRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ExecutionRecord v){store.put(TYPE,v.getId(),v,v.getJobId());} public ExecutionRecord get(String id){return store.get(TYPE,id,ExecutionRecord.class);}
	public List<ExecutionRecord> getAll(){return store.all(TYPE,ExecutionRecord.class);}
}
