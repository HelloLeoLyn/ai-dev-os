package com.aidevos.orchestrator.schedule;
import java.util.List; import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository;
@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
public class PostgresScheduleRepository implements ScheduleRepository {
	private static final String TYPE="schedule"; private final PostgresDocumentStore store; public PostgresScheduleRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ScheduledTask v){store.put(TYPE,v.getId(),v,v.getTaskId());} public ScheduledTask get(String id){return store.get(TYPE,id,ScheduledTask.class);}
	public List<ScheduledTask> getAll(){return store.all(TYPE,ScheduledTask.class);} public void remove(String id){store.delete(TYPE,id);}
}
