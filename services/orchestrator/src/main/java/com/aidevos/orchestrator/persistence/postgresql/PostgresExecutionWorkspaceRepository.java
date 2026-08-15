package com.aidevos.orchestrator.persistence.postgresql;

import com.aidevos.orchestrator.execution.workspace.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix="aidevos.persistence", name="type", havingValue="postgresql")
class PostgresExecutionWorkspaceRepository implements ExecutionWorkspaceRepository {
    private static final String TYPE="execution-workspace";
    private final PostgresDocumentStore store;
    PostgresExecutionWorkspaceRepository(PostgresDocumentStore store){this.store=store;}
    public void save(ExecutionWorkspace value){store.put(TYPE,value.getId(),value,"task:"+value.getTaskId());}
    public ExecutionWorkspace findByTaskId(String taskId){return store.allBySecondary(TYPE,"task:"+taskId,ExecutionWorkspace.class).stream().findFirst().orElse(null);}
    public ExecutionWorkspace get(String id){return store.get(TYPE,id,ExecutionWorkspace.class);}
    public List<ExecutionWorkspace> getAll(){return store.all(TYPE,ExecutionWorkspace.class);}
}
