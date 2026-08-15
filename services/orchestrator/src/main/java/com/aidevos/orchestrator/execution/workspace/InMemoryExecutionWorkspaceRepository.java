package com.aidevos.orchestrator.execution.workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="aidevos.persistence", name="type", havingValue="in-memory", matchIfMissing=true)
public class InMemoryExecutionWorkspaceRepository implements ExecutionWorkspaceRepository {
    private final Map<String, ExecutionWorkspace> values = new LinkedHashMap<>();
    public synchronized void save(ExecutionWorkspace value){values.put(value.getId(), value);}
    public synchronized ExecutionWorkspace findByTaskId(String taskId){return values.values().stream().filter(v->taskId.equals(v.getTaskId())).findFirst().orElse(null);}
    public synchronized ExecutionWorkspace get(String id){return values.get(id);}
    public synchronized List<ExecutionWorkspace> getAll(){return new ArrayList<>(values.values());}
}
