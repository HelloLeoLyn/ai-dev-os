package com.aidevos.orchestrator.manager;
import java.util.List; import com.aidevos.orchestrator.model.AgentDefinition; import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Repository;
@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
public class PostgresAgentRepository implements AgentRepository {
	private static final String TYPE="agent"; private final PostgresDocumentStore store; public PostgresAgentRepository(PostgresDocumentStore store){this.store=store;}
	public void save(AgentDefinition v){store.put(TYPE,v.getName(),v,null);} public AgentDefinition get(String id){return store.get(TYPE,id,AgentDefinition.class);}
	public List<AgentDefinition> getAll(){return store.all(TYPE,AgentDefinition.class);} public void remove(String id){store.delete(TYPE,id);}
}
