package com.aidevos.orchestrator.remote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;

@Service
public class RemotePushApprovalService {
    private final RemotePushApprovalRepository repository;
    private final AuditService audit;
    private volatile com.aidevos.orchestrator.delivery.DeliveryPipelineService deliveryPipelineService;
    public RemotePushApprovalService(RemotePushApprovalRepository repository, AuditService audit){this.repository=repository;this.audit=audit;}
    @org.springframework.beans.factory.annotation.Autowired(required=false) @org.springframework.context.annotation.Lazy public void setDeliveryPipelineService(com.aidevos.orchestrator.delivery.DeliveryPipelineService value){deliveryPipelineService=value;}
    public synchronized RemotePushApproval request(String taskId,String workspaceId,String branch,String commitId,String hash,String remote){
        RemotePushApproval existing=repository.findPending(taskId,commitId,hash,remote,branch);
        if(existing!=null)return existing;
        RemotePushApproval value=new RemotePushApproval("remote-push-"+UUID.randomUUID(),taskId,workspaceId,branch,commitId,hash,remote,"refs/heads/"+branch,Instant.now());
        repository.save(value); flow(EventType.REMOTE_PUSH_APPROVAL_REQUESTED,value,"",value.getStatus().name()); return value;
    }
    public synchronized RemotePushApproval approve(String id){RemotePushApproval v=require(id);String from=v.getStatus().name();v.approve();repository.save(v);if(!from.equals(v.getStatus().name())){flow(EventType.REMOTE_PUSH_APPROVAL_APPROVED,v,from,v.getStatus().name());if(deliveryPipelineService!=null)deliveryPipelineService.advanceIfExists(v.getTaskId());}return v;}
    public synchronized RemotePushApproval reject(String id){RemotePushApproval v=require(id);String from=v.getStatus().name();v.reject();repository.save(v);return v;}
    public synchronized boolean consume(RemotePushApproval v){if(!v.consume())return false;repository.save(v);flow(EventType.REMOTE_PUSH_APPROVAL_CONSUMED,v,RemotePushApprovalStatus.APPROVED.name(),v.getStatus().name());return true;}
    public RemotePushApproval get(String id){return repository.get(id);} public List<RemotePushApproval> getAll(){return repository.getAll();} public List<RemotePushApproval> getByTask(String taskId){return repository.getByTask(taskId);}
    private RemotePushApproval require(String id){RemotePushApproval v=repository.get(id);if(v==null)throw new IllegalArgumentException("Remote push approval not found: "+id);return v;}
    private void flow(EventType event,RemotePushApproval v,String from,String to){audit.remoteEvent(event,v.getTaskId(),v.getApprovalId(),v.getCommitId(),from,to,"Remote push approval",java.util.Map.of("approvalId",v.getApprovalId(),"executionWorkspaceId",v.getExecutionWorkspaceId(),"branch",v.getExecutionBranch(),"commitHash",v.getCommitHash(),"remote",v.getRemote(),"authority",RemotePushApproval.AUTHORITY,"operation",RemotePushApproval.OPERATION));}
}
