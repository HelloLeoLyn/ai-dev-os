package com.aidevos.orchestrator.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix="aidevos.persistence", name="type", matchIfMissing=true, havingValue="in-memory")
public class InMemoryRemotePushApprovalRepository implements RemotePushApprovalRepository {
    private final Map<String,RemotePushApproval> values = new LinkedHashMap<>();
    public synchronized void save(RemotePushApproval v){values.put(v.getApprovalId(),v);}
    public synchronized RemotePushApproval get(String id){return values.get(id);}
    public synchronized List<RemotePushApproval> getAll(){return new ArrayList<>(values.values());}
    public synchronized RemotePushApproval findPending(String taskId,String commitId,String hash,String remote,String branch){
        return values.values().stream().filter(v->taskId.equals(v.getTaskId())&&commitId.equals(v.getCommitId())&&hash.equals(v.getCommitHash())&&remote.equals(v.getRemote())&&branch.equals(v.getExecutionBranch()))
            .filter(v->v.getStatus()==RemotePushApprovalStatus.PENDING||v.getStatus()==RemotePushApprovalStatus.APPROVED).findFirst().orElse(null);
    }
}
