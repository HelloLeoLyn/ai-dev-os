package com.aidevos.orchestrator.task;
import java.util.List;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
public class PostgresTaskRepository implements TaskRepository {
	private static final String TYPE="task"; private final PostgresDocumentStore store;
	public PostgresTaskRepository(PostgresDocumentStore store){this.store=store;}
	public void save(TaskDefinition v){store.put(TYPE,v.getId(),v,null);} public TaskDefinition get(String id){return store.get(TYPE,id,TaskDefinition.class);}
	public List<TaskDefinition> getAll(){return store.all(TYPE,TaskDefinition.class);} public void remove(String id){store.delete(TYPE,id);}
}
