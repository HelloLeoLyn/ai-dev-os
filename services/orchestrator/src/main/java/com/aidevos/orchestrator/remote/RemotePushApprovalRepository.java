package com.aidevos.orchestrator.remote;

import java.util.List;

public interface RemotePushApprovalRepository {
    void save(RemotePushApproval approval);
    RemotePushApproval get(String id);
    List<RemotePushApproval> getAll();
    default List<RemotePushApproval> getByTask(String taskId){return getAll().stream().filter(v->taskId!=null&&taskId.equals(v.getTaskId())).toList();}
    RemotePushApproval findPending(String taskId, String commitId, String commitHash,
            String remote, String branch);
}
