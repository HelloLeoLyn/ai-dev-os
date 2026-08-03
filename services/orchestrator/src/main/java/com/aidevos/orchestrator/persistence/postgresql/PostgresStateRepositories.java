package com.aidevos.orchestrator.persistence.postgresql;

import java.util.Comparator;
import java.util.List;
import com.aidevos.orchestrator.approval.*;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.plan.approval.*;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.planner.replan.*;
import com.aidevos.orchestrator.tool.approval.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresJobRepository implements JobRepository {
	private static final String TYPE="job"; private final PostgresDocumentStore store;
	PostgresJobRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ExecutionJob v){store.put(TYPE,v.getId(),PersistenceSnapshots.Job.of(v),v.getStatus().name());}
	public ExecutionJob get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.Job.class);return v==null?null:v.value();}
	public List<ExecutionJob> getAll(){return store.all(TYPE,PersistenceSnapshots.Job.class).stream().map(PersistenceSnapshots.Job::value).sorted(Comparator.comparing(ExecutionJob::getCreatedAt).thenComparing(ExecutionJob::getId)).toList();}
	public List<ExecutionJob> getByStatus(JobStatus status){return getAll().stream().filter(v->v.getStatus()==status).toList();}
	public void remove(String id){store.delete(TYPE,id);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresCodingApprovalRepository implements CodingApprovalRepository {
	private static final String TYPE="coding-approval"; private final PostgresDocumentStore store;
	PostgresCodingApprovalRepository(PostgresDocumentStore store){this.store=store;}
	public void save(CodingApprovalRequest v){store.put(TYPE,v.getId(),PersistenceSnapshots.CodingApproval.of(v),v.getJobId());}
	public CodingApprovalRequest get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.CodingApproval.class);return v==null?null:v.value();}
	public List<CodingApprovalRequest> getAll(){return store.all(TYPE,PersistenceSnapshots.CodingApproval.class).stream().map(PersistenceSnapshots.CodingApproval::value).toList();}
	public CodingApprovalRequest findReusable(String taskId,String jobId){return getAll().stream().filter(v->jobId!=null?jobId.equals(v.getJobId()):v.getJobId()==null&&taskId!=null&&taskId.equals(v.getTaskId())).filter(v->v.getStatus()==ApprovalStatus.PENDING||v.getStatus()==ApprovalStatus.APPROVED).reduce((a,b)->b).orElse(null);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresToolApprovalRepository implements ToolApprovalRepository {
	private static final String TYPE="tool-approval"; private final PostgresDocumentStore store;
	PostgresToolApprovalRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ToolApprovalRequest v){store.put(TYPE,v.getId(),PersistenceSnapshots.ToolApproval.of(v),v.getJobId());}
	public ToolApprovalRequest get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.ToolApproval.class);return v==null?null:v.value();}
	public List<ToolApprovalRequest> getAll(){return store.all(TYPE,PersistenceSnapshots.ToolApproval.class).stream().map(PersistenceSnapshots.ToolApproval::value).toList();}
	public ToolApprovalRequest findReusable(String jobId,String invocationId,String providerId,String toolName,String argumentsHash,String workspace,String permissionLevel){return getAll().stream().filter(v->same(v.getJobId(),jobId)&&v.getInvocationId().equals(invocationId)&&v.getProviderId().equals(providerId)&&v.getToolName().equals(toolName)&&v.getArgumentsHash().equals(argumentsHash)&&same(v.getWorkspace(),workspace)&&v.getPermissionLevel().equals(permissionLevel)).filter(v->v.getStatus()==ApprovalStatus.PENDING||v.getStatus()==ApprovalStatus.APPROVED).reduce((a,b)->b).orElse(null);}
	private boolean same(String a,String b){return a==null?b==null:a.equals(b);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresPlanApprovalRepository implements PlanApprovalRepository {
	private static final String TYPE="plan-approval"; private final PostgresDocumentStore store;
	PostgresPlanApprovalRepository(PostgresDocumentStore store){this.store=store;}
	public synchronized PlanApprovalRequest save(PlanApprovalRequest v){PlanApprovalRequest frozen=getAll().stream().filter(x->x.getPlanId().equals(v.getPlanId())&&x.getPlanVersion()==v.getPlanVersion()).findFirst().orElse(null);if(frozen!=null&&!frozen.getPlanSnapshotHash().equals(v.getPlanSnapshotHash()))throw new IllegalStateException("Plan version content is frozen: "+v.getPlanId()+":"+v.getPlanVersion());PlanApprovalRequest existing=find(v.getPlanId(),v.getPlanVersion(),v.getRequestId(),v.getPlanSnapshotHash());if(existing!=null&&!existing.getId().equals(v.getId()))return existing;store.put(TYPE,v.getId(),PersistenceSnapshots.PlanApproval.of(v),v.getPlanId()+":"+v.getPlanVersion());return v;}
	public PlanApprovalRequest get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.PlanApproval.class);return v==null?null:v.value();}
	public List<PlanApprovalRequest> getAll(){return store.all(TYPE,PersistenceSnapshots.PlanApproval.class).stream().map(PersistenceSnapshots.PlanApproval::value).toList();}
	public PlanApprovalRequest find(String planId,int version,String requestId,String hash){return getAll().stream().filter(v->v.getPlanId().equals(planId)&&v.getPlanVersion()==version&&v.getRequestId().equals(requestId)&&v.getPlanSnapshotHash().equals(hash)).findFirst().orElse(null);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresReplanRequestRepository implements ReplanRequestRepository {
	private static final String TYPE="replan-request"; private final PostgresDocumentStore store;
	PostgresReplanRequestRepository(PostgresDocumentStore store){this.store=store;}
	public void save(ReplanRequest v){store.put(TYPE,v.id(),v,v.failedPlanRunId());} public ReplanRequest get(String id){return store.get(TYPE,id,ReplanRequest.class);}
	public List<ReplanRequest> getAll(){return store.all(TYPE,ReplanRequest.class);} public ReplanRequest findByPlanRun(String id){return getAll().stream().filter(v->v.failedPlanRunId().equals(id)).findFirst().orElse(null);}
}

@Repository @ConditionalOnProperty(prefix="aidevos.persistence",name="type",havingValue="postgresql")
class PostgresPlanRunRepository implements PlanRunRepository {
	private static final String TYPE="plan-run"; private final PostgresDocumentStore store;
	PostgresPlanRunRepository(PostgresDocumentStore store){this.store=store;}
	public synchronized void create(String approvalId,PlanRun run){if(findRunIdByApproval(approvalId)!=null)throw new IllegalStateException("Plan approval has already started a run");save(run);}
	public void save(PlanRun v){store.put(TYPE,v.getId(),PersistenceSnapshots.Run.of(v),v.getApprovalId());}
	public PlanRun get(String id){var v=store.get(TYPE,id,PersistenceSnapshots.Run.class);return v==null?null:v.value();}
	public List<PlanRun> getAll(){return store.all(TYPE,PersistenceSnapshots.Run.class).stream().map(PersistenceSnapshots.Run::value).toList();}
	public String findRunIdByApproval(String id){return getAll().stream().filter(v->id.equals(v.getApprovalId())).map(PlanRun::getId).findFirst().orElse(null);}
	public void remove(String approvalId,String runId){store.delete(TYPE,runId);}
}
